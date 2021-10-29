package io.github.skauppin.maven.buildcache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.maven.plugin.MojoExecution;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class MojoExecUtilTest {

  private static final String[] IGNORED_DEFAULT_PHASES =
      {"validate", "initialize", "generate-sources", "process-sources", "generate-resources",
          "process-resources", "generate-test-sources", "process-test-sources",
          "generate-test-resources", "process-test-resources", "prepare-package", "package",
          "verify", "install", "deploy", "clean"};

  @Test
  public void testIsBuildCacheIgnoredPhase() {
    MojoExecution mojoExecution = Mockito.mock(MojoExecution.class);
    for (int i = 0; i < IGNORED_DEFAULT_PHASES.length; i++) {
      Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn(IGNORED_DEFAULT_PHASES[i]);
      assertTrue(MojoExecUtil.isBuildCacheIgnoredPhase(mojoExecution));
    }

    Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn("compile");
    assertFalse(MojoExecUtil.isBuildCacheIgnoredPhase(mojoExecution));
    Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn("test-compile");
    assertFalse(MojoExecUtil.isBuildCacheIgnoredPhase(mojoExecution));
    Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn("test");
    assertFalse(MojoExecUtil.isBuildCacheIgnoredPhase(mojoExecution));
  }

  @Test
  public void testIsCompilePhase() {
    MojoExecution mojoExecution = Mockito.mock(MojoExecution.class);
    Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn("compile");
    assertTrue(MojoExecUtil.isCompileRelatedPhase(mojoExecution));
  }

  @Test
  public void testIsCompileOrProcessPhase() {
    MojoExecution mojoExecution = Mockito.mock(MojoExecution.class);
    Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn("compile");
    assertTrue(MojoExecUtil.isCompileRelatedPhase(mojoExecution));
    Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn("process-classes");
    assertTrue(MojoExecUtil.isCompileRelatedPhase(mojoExecution));
  }

  @Test
  public void testIsTestCompilePhase() {
    MojoExecution mojoExecution = Mockito.mock(MojoExecution.class);
    Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn("test-compile");
    assertTrue(MojoExecUtil.isTestCompileRelatedPhase(mojoExecution));
  }

  @Test
  public void testIsTestCompileOrProcessPhase() {
    MojoExecution mojoExecution = Mockito.mock(MojoExecution.class);
    Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn("test-compile");
    assertTrue(MojoExecUtil.isTestCompileRelatedPhase(mojoExecution));
    Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn("process-test-classes");
    assertTrue(MojoExecUtil.isTestCompileRelatedPhase(mojoExecution));
  }

  @Test
  public void testIsTestPhase() {
    MojoExecution mojoExecution = Mockito.mock(MojoExecution.class);
    Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn("test");
    assertTrue(MojoExecUtil.isTestRelatedPhase(mojoExecution));
  }
}
