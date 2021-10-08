package io.github.skauppin.maven.buildcache;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionEvent.Type;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role = EventSpy.class, hint = "buildcache")
public class BuildCacheEventSpy extends AbstractEventSpy {

  @Requirement
  private Logger logger;

  @Requirement
  private ExecutionTimeRegister executionTimeRegister;

  @Requirement
  private BuildCache buildCache;

  @Override
  public void onEvent(Object event) throws Exception {

    if (event instanceof ExecutionEvent) {
      ExecutionEvent exec = (ExecutionEvent) event;
      MavenSession session = exec.getSession();

      if (exec.getType() == Type.SessionStarted) {
        buildCache.initializeSession(session);

      } else if (exec.getType() == Type.ProjectStarted && buildCache.isInitialized()) {
        buildCache.cleanProject(session);

      } else if (exec.getType() == Type.ProjectSucceeded) {
        ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
        boolean buildCacheDebug = buildCache.isBuildCacheDebug();

        if (projectStatus.getMainCompile().isVisited()
            && !projectStatus.getMainCompile().isCacheHit()) {
          logger.info(String.format("buildcache: caching main classes (%s)",
              projectStatus.getMainCompile().getPhaseHash()));
          buildCache.cacheMainClasses(session);
        }
        if (projectStatus.getMainCompile().isVisited() && buildCacheDebug) {
          buildCache.writeMainCompileDetails(session);
        }

        if (projectStatus.getTestCompile().isVisited()
            && !projectStatus.getTestCompile().isCacheHit()) {
          logger.info(String.format("buildcache: caching test classes (%s)",
              projectStatus.getTestCompile().getPhaseHash()));
          buildCache.cacheTestClasses(session);
        }
        if (projectStatus.getTestCompile().isVisited() && buildCacheDebug) {
          buildCache.writeTestCompileDetails(session);
        }

        if (projectStatus.getTest().isVisited() && !projectStatus.getTest().isCacheHit()) {
          logger.info(String.format("buildcache: caching test execution (%s)",
              projectStatus.getTest().getPhaseHash()));
          buildCache.cacheTestExecution(session);
        }
        if (projectStatus.getTest().isVisited() && buildCacheDebug) {
          buildCache.writeTestExecutionDetails(session);
        }

        if (projectStatus.getIntegrationTest().isVisited()
            && !projectStatus.getIntegrationTest().isCacheHit()) {
          logger.info(String.format("buildcache: caching it-test execution (%s)",
              projectStatus.getIntegrationTest().getPhaseHash()));
          buildCache.cacheIntegrationTestExecution(session);
        }
        if (projectStatus.getIntegrationTest().isVisited() && buildCacheDebug) {
          buildCache.writeIntegrationTestExecutionDetails(session);
        }

        if (buildCacheDebug) {
          logger.info(String.format("buildcache: %s %s",
              BuildCacheImpl.getProjectId(session.getCurrentProject()), projectStatus));
        }
      }

    } else if (event instanceof MavenExecutionResult) {
      if (buildCache.isBuildCacheProfile()) {
        executionTimeRegister.logExecutionTimes();
      }
    }
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

}
