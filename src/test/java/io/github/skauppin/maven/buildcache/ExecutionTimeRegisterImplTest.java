package io.github.skauppin.maven.buildcache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collection;
import java.util.Iterator;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import io.github.skauppin.maven.buildcache.ExecutionTimeRegisterImpl;
import io.github.skauppin.maven.buildcache.ExecutionTimeRegisterImpl.MojoExecutionTime;
import io.github.skauppin.maven.buildcache.ExecutionTimeRegisterImpl.ProjectExecution;

public class ExecutionTimeRegisterImplTest {

  @Test
  public void testTimeRegister() {
    MavenProject project = Mockito.mock(MavenProject.class);
    Mockito.when(project.getGroupId()).thenReturn("com.test");
    Mockito.when(project.getArtifactId()).thenReturn("test-lib");
    Mockito.when(project.getName()).thenReturn("TestLib");

    MojoExecution mojoMainCompile = Mockito.mock(MojoExecution.class);
    Mockito.when(mojoMainCompile.getLifecyclePhase()).thenReturn("compile");
    Mockito.when(mojoMainCompile.getGroupId()).thenReturn("com.test");
    Mockito.when(mojoMainCompile.getArtifactId()).thenReturn("plugin");
    Mockito.when(mojoMainCompile.getVersion()).thenReturn("1.0.0");
    Mockito.when(mojoMainCompile.getGoal()).thenReturn("main-compile");

    MojoExecution mojoTestCompile = Mockito.mock(MojoExecution.class);
    Mockito.when(mojoTestCompile.getLifecyclePhase()).thenReturn("test-compile");
    Mockito.when(mojoTestCompile.getGroupId()).thenReturn("com.test");
    Mockito.when(mojoTestCompile.getArtifactId()).thenReturn("plugin2");
    Mockito.when(mojoTestCompile.getVersion()).thenReturn("1.0.1");
    Mockito.when(mojoTestCompile.getGoal()).thenReturn("test-compile");

    Logger logger = Mockito.mock(Logger.class);

    ExecutionTimeRegisterImpl register = new ExecutionTimeRegisterImpl();
    register.setLogger(logger);

    register.record(project, mojoMainCompile, 10, true);
    register.record(project, mojoTestCompile, 100, false);

    assertEquals(1, register.getProjectExecutions().size());
    ProjectExecution projectExec = register.getProjectExecutions().get("com.test:test-lib");
    assertNotNull(projectExec);

    Collection<MojoExecutionTime> mojoExecTimes = projectExec.getMojoExecutionTimes();
    assertEquals(2, mojoExecTimes.size());

    Iterator<MojoExecutionTime> execTimeIt = mojoExecTimes.iterator();

    MojoExecutionTime execTimeMainCompile = execTimeIt.next();
    assertEquals(mojoMainCompile, execTimeMainCompile.getMojoExecution());
    assertEquals(10, execTimeMainCompile.getExecTimeMillis());
    assertTrue(execTimeMainCompile.isSkipped());

    MojoExecutionTime execTimeTestCompile = execTimeIt.next();
    assertEquals(mojoTestCompile, execTimeTestCompile.getMojoExecution());
    assertEquals(100, execTimeTestCompile.getExecTimeMillis());
    assertFalse(execTimeTestCompile.isSkipped());

    register.logExecutionTimes();
    Mockito.verify(logger, Mockito.times(1)).info("TestLib");
    Mockito.verify(logger, Mockito.times(1)).info(
        "  compile ................... com.test:plugin:1.0.0:main-compile ........................................ [  0.010 s] (cache hit)");
    Mockito.verify(logger, Mockito.times(1)).info(
        "  test-compile .............. com.test:plugin2:1.0.1:test-compile ....................................... [  0.100 s] ");
    Mockito.verifyNoMoreInteractions(logger);
  }
}
