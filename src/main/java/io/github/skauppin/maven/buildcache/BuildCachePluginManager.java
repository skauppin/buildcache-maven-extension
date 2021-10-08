package io.github.skauppin.maven.buildcache;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.DefaultBuildPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginManagerException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import io.github.skauppin.maven.buildcache.ProjectBuildStatus.Phase;

@Component(role = BuildPluginManager.class)
public class BuildCachePluginManager extends DefaultBuildPluginManager implements Initializable {

  @Requirement
  private Logger logger;

  @Requirement
  private ExecutionTimeRegister executionTimeRegister;

  @Requirement
  private BuildCache buildCache;

  private MojoExecutor delegate = (s, m) -> super.executeMojo(s, m);

  private List<PhaseExecution> phaseExecutions;

  @Override
  public void initialize() throws InitializationException {
    phaseExecutions =
        Arrays.asList(new MainCompileExecution(buildCache), new TestCompileExecution(buildCache),
            new TestExecution(buildCache), new IntegrationTestExecution(buildCache));
  }

  @Override
  public void executeMojo(MavenSession session, MojoExecution mojoExecution)
      throws MojoFailureException, MojoExecutionException, PluginConfigurationException,
      PluginManagerException {

    StopWatch watch = StopWatch.createStarted();

    boolean shouldDelegate = execute(session, mojoExecution);
    if (shouldDelegate) {
      delegate.executeMojo(session, mojoExecution);
    }

    executionTimeRegister.record(session.getCurrentProject(), mojoExecution, watch.getTime(),
        !shouldDelegate);
  }

  private boolean execute(MavenSession session, MojoExecution mojoExecution)
      throws MojoFailureException, MojoExecutionException, PluginConfigurationException,
      PluginManagerException {

    if (buildCache.isInitializationError()) {
      throw new MojoExecutionException(buildCache.getInitializationErrorMessage());
    }

    if (!buildCache.isInitialized()) {
      throw new MojoExecutionException(
          "buildcache has not been initialized. Are you sure buildcache extension is"
              + " defined in .mvn/extensions.xml");
    }

    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session, mojoExecution);
    if (projectStatus.isBuildCacheDisabled() //
        || MojoExecUtil.isBuildCacheIgnoredPhase(mojoExecution) //
        || projectStatus.isMavenMainSkip()) {
      return true;
    }

    boolean delegate = true;

    for (PhaseExecution phaseExecution : phaseExecutions) {
      if (phaseExecution.shouldExecuteOnMojo(mojoExecution, projectStatus)) {
        ProjectBuildStatus.Phase phase = phaseExecution.getPhase(projectStatus);
        if (phase.isCacheHit()) {
          delegate = false;

        } else if (phaseExecution.shouldUseCachedEntry(projectStatus)) {
          boolean cacheHit = phaseExecution.isCacheHit(session);
          if (cacheHit) {
            logger.info(String.format("buildcache: cache hit - %s (%s)",
                phaseExecution.getCacheHitLogString(), phase.getPhaseHash()));
            phase.setCacheHit();
            delegate = false;
          }
        }

        phase.setVisited();
      }
    }
    return delegate;
  }

  void setLogger(Logger logger) {
    this.logger = logger;
  }

  void setExecutionTimeRegister(ExecutionTimeRegister executionTimeRegister) {
    this.executionTimeRegister = executionTimeRegister;
  }

  void setBuildCache(BuildCache buildCache) {
    this.buildCache = buildCache;
  }

  void setDelegate(MojoExecutor delegate) {
    this.delegate = delegate;
  }

  @FunctionalInterface
  interface MojoExecutor {
    public void executeMojo(MavenSession session, MojoExecution mojoExecution)
        throws MojoFailureException, MojoExecutionException, PluginConfigurationException,
        PluginManagerException;
  }

  abstract static class PhaseExecution {

    final BuildCache buildCache;

    PhaseExecution(BuildCache buildCache) {
      this.buildCache = buildCache;
    }

    public abstract boolean shouldExecuteOnMojo(MojoExecution mojoExecution,
        ProjectBuildStatus projectStatus);

    public abstract ProjectBuildStatus.Phase getPhase(ProjectBuildStatus projectStatus);

    public abstract boolean shouldUseCachedEntry(ProjectBuildStatus projectStatus);

    public abstract boolean isCacheHit(MavenSession session);

    public abstract String getCacheHitLogString();
  }

  static class MainCompileExecution extends PhaseExecution {

    MainCompileExecution(BuildCache buildCache) {
      super(buildCache);
    }

    @Override
    public boolean shouldExecuteOnMojo(MojoExecution mojoExecution,
        ProjectBuildStatus projectStatus) {
      return MojoExecUtil.isCompileRelatedPhase(mojoExecution);
    }

    @Override
    public Phase getPhase(ProjectBuildStatus projectStatus) {
      return projectStatus.getMainCompile();
    }

    @Override
    public boolean shouldUseCachedEntry(ProjectBuildStatus projectStatus) {
      return projectStatus.getMainCompile().notVisited();
    }

    @Override
    public boolean isCacheHit(MavenSession session) {
      return buildCache.useCachedMainClasses(session);
    }

    @Override
    public String getCacheHitLogString() {
      return "using cached main classes";
    }
  }

  static class TestCompileExecution extends PhaseExecution {

    TestCompileExecution(BuildCache buildCache) {
      super(buildCache);
    }

    @Override
    public boolean shouldExecuteOnMojo(MojoExecution mojoExecution,
        ProjectBuildStatus projectStatus) {
      return MojoExecUtil.isTestCompileRelatedPhase(mojoExecution)
          && !projectStatus.isMavenTestSkip();
    }

    @Override
    public Phase getPhase(ProjectBuildStatus projectStatus) {
      return projectStatus.getTestCompile();
    }

    @Override
    public boolean shouldUseCachedEntry(ProjectBuildStatus projectStatus) {
      return projectStatus.getTestCompile().notVisited()
          && projectStatus.getMainCompile().isCacheHit();
    }

    @Override
    public boolean isCacheHit(MavenSession session) {
      return buildCache.useCachedTestClasses(session);
    }

    @Override
    public String getCacheHitLogString() {
      return "using cached test classes";
    }
  }

  static class TestExecution extends PhaseExecution {

    TestExecution(BuildCache buildCache) {
      super(buildCache);
    }

    @Override
    public boolean shouldExecuteOnMojo(MojoExecution mojoExecution,
        ProjectBuildStatus projectStatus) {
      return MojoExecUtil.isTestRelatedPhase(mojoExecution) && isFullTestExecution(projectStatus);
    }

    private boolean isFullTestExecution(ProjectBuildStatus projectStatus) {
      return !projectStatus.isMavenTestSkip() && !projectStatus.isSkipTests()
          && !projectStatus.isTestSubset();
    }

    @Override
    public Phase getPhase(ProjectBuildStatus projectStatus) {
      return projectStatus.getTest();
    }

    @Override
    public boolean shouldUseCachedEntry(ProjectBuildStatus projectStatus) {
      return projectStatus.getTest().notVisited() && projectStatus.getTestCompile().isCacheHit();
    }

    @Override
    public boolean isCacheHit(MavenSession session) {
      return buildCache.isTestExecutionCacheHit(session);
    }

    @Override
    public String getCacheHitLogString() {
      return "test execution success";
    }

  }

  static class IntegrationTestExecution extends PhaseExecution {

    IntegrationTestExecution(BuildCache buildCache) {
      super(buildCache);
    }

    @Override
    public boolean shouldExecuteOnMojo(MojoExecution mojoExecution,
        ProjectBuildStatus projectStatus) {
      return MojoExecUtil.isIntegrationTestRelatedPhase(mojoExecution)
          && isFullIntegrationTestExecution(projectStatus);
    }

    private boolean isFullIntegrationTestExecution(ProjectBuildStatus projectStatus) {
      return !projectStatus.isMavenTestSkip() && !projectStatus.isSkipTests()
          && !projectStatus.isSkipItTests() && !projectStatus.isItTestSubset();
    }

    @Override
    public Phase getPhase(ProjectBuildStatus projectStatus) {
      return projectStatus.getIntegrationTest();
    }

    @Override
    public boolean shouldUseCachedEntry(ProjectBuildStatus projectStatus) {
      return projectStatus.getIntegrationTest().notVisited()
          && projectStatus.getTestCompile().isCacheHit();
    }

    @Override
    public boolean isCacheHit(MavenSession session) {
      return buildCache.isIntegrationTestExecutionCacheHit(session);
    }

    @Override
    public String getCacheHitLogString() {
      return "integration test execution success";
    }
  }
}
