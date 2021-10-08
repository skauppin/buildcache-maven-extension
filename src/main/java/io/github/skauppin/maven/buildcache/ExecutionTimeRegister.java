package io.github.skauppin.maven.buildcache;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;

public interface ExecutionTimeRegister {

  void record(MavenProject project, MojoExecution mojoExecution, long execTimeMillis,
      boolean skipped);

  public void logExecutionTimes();
}
