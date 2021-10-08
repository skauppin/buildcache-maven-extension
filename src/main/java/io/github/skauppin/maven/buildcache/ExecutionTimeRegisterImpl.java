package io.github.skauppin.maven.buildcache;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.StringUtils;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role = ExecutionTimeRegister.class)
public class ExecutionTimeRegisterImpl implements ExecutionTimeRegister {

  @Requirement
  private Logger logger;

  private Map<String, ProjectExecution> projectExecutions =
      Collections.synchronizedMap(new LinkedHashMap<>());

  @Override
  public void record(MavenProject project, MojoExecution mojoExecution, long execTimeMillis,
      boolean skipped) {
    String key = BuildCacheImpl.getProjectId(project);
    projectExecutions.computeIfAbsent(key, s -> new ProjectExecution(project)).record(mojoExecution,
        execTimeMillis, skipped);
  }

  Map<String, ProjectExecution> getProjectExecutions() {
    return this.projectExecutions;
  }

  @Override
  public void logExecutionTimes() {
    projectExecutions.values().forEach(projectExec -> {
      logger.info(String.format("%s", projectExec.getProject().getName()));
      projectExec.getMojoExecutionTimes().forEach(mojoExec -> {
        logger.info(String.format("  %s [%s s] %s", toString(mojoExec.getMojoExecution()),
            padLeft(toSeconds(mojoExec.getExecTimeMillis()), 5),
            mojoExec.isSkipped() ? "(cache hit)" : ""));
      });
    });
  }

  private String toString(MojoExecution mojo) {
    return String.format("%s %s", padRight(mojo.getLifecyclePhase(), 27),
        padRight(String.format("%s:%s:%s:%s", mojo.getGroupId(), mojo.getArtifactId(),
            mojo.getVersion(), mojo.getGoal()), 75));
  }

  private String toSeconds(long millis) {
    return toString(((float) millis) / 1000);
  }

  private String toString(float f) {
    return String.format("%6.3f", f);
  }

  private String padRight(String s, int n) {
    return StringUtils.rightPad(s + " ", n, ".");
  }

  private String padLeft(String s, int n) {
    return StringUtils.rightPad(" " + s, n, ".");
  }

  void setLogger(Logger logger) {
    this.logger = logger;
  }

  public static class ProjectExecution {
    private final MavenProject project;
    private List<MojoExecutionTime> mojoExecTimes =
        Collections.synchronizedList(new LinkedList<>());

    public ProjectExecution(MavenProject project) {
      this.project = project;
    }

    public void record(MojoExecution mojoExecution, long execTimeMillis, boolean skipped) {
      mojoExecTimes.add(new MojoExecutionTime(mojoExecution, execTimeMillis, skipped));
    }

    public MavenProject getProject() {
      return project;
    }

    public Collection<MojoExecutionTime> getMojoExecutionTimes() {
      return this.mojoExecTimes;
    }
  }

  public static class MojoExecutionTime {
    private final MojoExecution mojoExecution;
    private final long execTimeMillis;
    private final boolean skipped;

    public MojoExecutionTime(MojoExecution mojoExecution, long execTimeMillis, boolean skipped) {
      this.mojoExecution = mojoExecution;
      this.execTimeMillis = execTimeMillis;
      this.skipped = skipped;
    }

    public MojoExecution getMojoExecution() {
      return mojoExecution;
    }

    public long getExecTimeMillis() {
      return execTimeMillis;
    }

    public boolean isSkipped() {
      return skipped;
    }
  }
}
