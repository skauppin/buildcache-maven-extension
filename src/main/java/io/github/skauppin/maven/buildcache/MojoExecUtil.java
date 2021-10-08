package io.github.skauppin.maven.buildcache;

import java.util.Arrays;
import java.util.List;
import org.apache.maven.plugin.MojoExecution;

public class MojoExecUtil {

  //@formatter:off

  public static final List<String> PHASE_COMPILE =
      Arrays.asList(
          "compile",
          "process-classes"
      );

  public static final List<String> PHASE_TEST_COMPILE =
      Arrays.asList(
          "test-compile",
          "process-test-classes"
      );

  public static final List<String> PHASE_TEST =
      Arrays.asList(
          "test"
      );

  public static final List<String> PHASE_IT =
      Arrays.asList(
          "pre-integration-test",
          "integration-test",
          "post-integration-test"
      );

  //@formatter:on

  private MojoExecUtil() {}

  public static boolean isBuildCacheIgnoredPhase(MojoExecution mojoExecution) {
    return !isCompileRelatedPhase(mojoExecution) && !isTestCompileRelatedPhase(mojoExecution)
        && !isTestRelatedPhase(mojoExecution) && !isIntegrationTestRelatedPhase(mojoExecution);
  }

  public static boolean isCompileRelatedPhase(MojoExecution mojoExecution) {
    return PHASE_COMPILE.contains(mojoExecution.getLifecyclePhase());
  }

  public static boolean isTestCompileRelatedPhase(MojoExecution mojoExecution) {
    return PHASE_TEST_COMPILE.contains(mojoExecution.getLifecyclePhase());
  }

  public static boolean isTestRelatedPhase(MojoExecution mojoExecution) {
    return PHASE_TEST.contains(mojoExecution.getLifecyclePhase());
  }

  public static boolean isIntegrationTestRelatedPhase(MojoExecution mojoExecution) {
    return PHASE_IT.contains(mojoExecution.getLifecyclePhase());
  }
}
