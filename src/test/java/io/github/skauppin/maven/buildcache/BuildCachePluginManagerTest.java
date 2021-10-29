package io.github.skauppin.maven.buildcache;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class BuildCachePluginManagerTest {

  private enum ExpectedTryCacheUse {
    NONE, MAIN_CLASSES, TEST_CLASSES, TEST_EXECUTION, INTEGRATION_TEST_EXECUTION
  }

  private MavenProject project;
  private MavenSession session;

  private BuildCache buildCache;

  private BuildCachePluginManager manager;

  private ProjectBuildStatus projectStatus;
  private ProjectBuildStatus.Phase mainCompilePhase;
  private ProjectBuildStatus.Phase testCompilePhase;
  private ProjectBuildStatus.Phase testExecutionPhase;
  private ProjectBuildStatus.Phase itTestExecutionPhase;

  private BuildCachePluginManager.MojoExecutor delegate;

  @BeforeEach
  public void init() throws InitializationException {
    buildCache = Mockito.mock(BuildCache.class);
    Mockito.when(buildCache.isInitialized()).thenReturn(true);

    manager = new BuildCachePluginManager();
    manager.setBuildCache(buildCache);
    manager.setExecutionTimeRegister(Mockito.mock(ExecutionTimeRegister.class));
    manager.setLogger(Mockito.mock(Logger.class));

    delegate = Mockito.mock(BuildCachePluginManager.MojoExecutor.class);
    manager.setDelegate(delegate);
    manager.initialize();

    project = Mockito.mock(MavenProject.class);
    session = Mockito.mock(MavenSession.class);

    Mockito.when(session.getCurrentProject()).thenReturn(project);

    mainCompilePhase = mockPhase();
    testCompilePhase = mockPhase();
    testExecutionPhase = mockPhase();
    itTestExecutionPhase = mockPhase();

    projectStatus = Mockito.mock(ProjectBuildStatus.class);
    Mockito.when(projectStatus.isBuildCacheEnabled()).thenReturn(true);
    Mockito.when(projectStatus.getMainCompile()).thenReturn(mainCompilePhase);
    Mockito.when(projectStatus.getTestCompile()).thenReturn(testCompilePhase);
    Mockito.when(projectStatus.getTest()).thenReturn(testExecutionPhase);
    Mockito.when(projectStatus.getIntegrationTest()).thenReturn(itTestExecutionPhase);
  }

  private ProjectBuildStatus.Phase mockPhase() {
    ProjectBuildStatus.Phase phase = Mockito.mock(ProjectBuildStatus.Phase.class);
    Mockito.when(phase.notVisited()).thenReturn(true);
    return phase;
  }

  @Test
  public void testBuildCacheNotInitialized() throws Exception {
    Mockito.when(buildCache.isInitialized()).thenReturn(false);
    assertThrows(MojoExecutionException.class, () -> manager.executeMojo(session, null));
  }

  @Test
  public void testBuildCacheInitializationError() throws Exception {
    Mockito.when(buildCache.isInitializationError()).thenReturn(true);
    assertThrows(MojoExecutionException.class, () -> manager.executeMojo(session, null));
  }

  @Test
  public void compileFirstDelegatedWhenNoCacheHit() throws Exception {

    boolean expectDelegated = true;
    runEnabledTest("compile", projectStatus, ExpectedTryCacheUse.MAIN_CLASSES, expectDelegated);

    Mockito.verify(mainCompilePhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void compileSecondDelegatedWhenNoCacheHit() throws Exception {

    Mockito.when(mainCompilePhase.isVisited()).thenReturn(true);
    Mockito.when(mainCompilePhase.notVisited()).thenReturn(false);

    boolean expectDelegated = true;
    runEnabledTest("compile", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);

    Mockito.verify(mainCompilePhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void compileFirstNotDelegatedWhenCacheHit() throws Exception {

    Mockito.when(buildCache.useCachedMainClasses(session)).thenReturn(true);

    boolean expectDelegated = false;
    runEnabledTest("compile", projectStatus, ExpectedTryCacheUse.MAIN_CLASSES, expectDelegated);

    Mockito.verify(mainCompilePhase, Mockito.times(1)).setCacheHit();
  }

  @Test
  public void compileSecondNotDelegatedWhenCacheHit() throws Exception {

    Mockito.when(mainCompilePhase.isCacheHit()).thenReturn(true);

    boolean expectDelegated = false;
    runEnabledTest("compile", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);

    Mockito.verify(mainCompilePhase, Mockito.never()).setCacheHit();
  }

  //
  // test-compile
  //

  @Test
  public void testCompileFirstDelegatedWhenNoCompileCacheHit() throws Exception {

    boolean expectDelegated = true;
    runEnabledTest("test-compile", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);

    Mockito.verify(testCompilePhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void testCompileDelegatedWhenMavenTestSkip() throws Exception {

    Mockito.when(projectStatus.isMavenTestSkip()).thenReturn(true);

    boolean expectDelegated = true;
    runEnabledTest("test-compile", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);

    Mockito.verify(testCompilePhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void testCompileFirstDelegatedWhenNoCacheHit() throws Exception {

    Mockito.when(mainCompilePhase.isCacheHit()).thenReturn(true);
    Mockito.when(buildCache.useCachedTestClasses(session)).thenReturn(false);

    boolean expectDelegated = true;
    runEnabledTest("test-compile", projectStatus, ExpectedTryCacheUse.TEST_CLASSES,
        expectDelegated);

    Mockito.verify(testCompilePhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void testCompileSecondDelegatedWhenNoCacheHit() throws Exception {

    Mockito.when(testCompilePhase.isVisited()).thenReturn(true);
    Mockito.when(testCompilePhase.notVisited()).thenReturn(false);

    boolean expectDelegated = true;
    runEnabledTest("test-compile", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);

    Mockito.verify(testCompilePhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void testCompileFirstNotDelegatedIfCacheHit() throws Exception {

    Mockito.when(mainCompilePhase.isCacheHit()).thenReturn(true);
    Mockito.when(buildCache.useCachedTestClasses(session)).thenReturn(true);

    boolean expectDelegated = false;
    runEnabledTest("test-compile", projectStatus, ExpectedTryCacheUse.TEST_CLASSES,
        expectDelegated);

    Mockito.verify(testCompilePhase, Mockito.times(1)).setCacheHit();
  }

  @Test
  public void testCompileSecondNotDelegatedIfCacheHit() throws Exception {

    Mockito.when(testCompilePhase.isCacheHit()).thenReturn(true);

    boolean expectDelegated = false;
    runEnabledTest("test-compile", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);
  }

  //
  // test
  //

  @Test
  public void testExecutionDelegatedWhenNoTestCompileCacheHit() throws Exception {

    boolean expectDelegated = true;
    runEnabledTest("test", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);

    Mockito.verify(testExecutionPhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void testExecutionDelegatedWhenSkipTests() throws Exception {

    Mockito.when(projectStatus.isSkipTests()).thenReturn(true);

    boolean expectDelegated = true;
    runEnabledTest("test", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);

    Mockito.verify(testExecutionPhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void testExecutionDelegatedWhenNoCacheHit() throws Exception {

    Mockito.when(testCompilePhase.isCacheHit()).thenReturn(true);

    boolean expectDelegated = true;
    runEnabledTest("test", projectStatus, ExpectedTryCacheUse.TEST_EXECUTION, expectDelegated);

    Mockito.verify(testExecutionPhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void testExecutionSecondDelegatedWhenNoCacheHit() throws Exception {

    Mockito.when(testExecutionPhase.isVisited()).thenReturn(true);
    Mockito.when(testExecutionPhase.notVisited()).thenReturn(false);

    boolean expectDelegated = true;
    runEnabledTest("test", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);

    Mockito.verify(testExecutionPhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void testExecutionFirstNotDelegatedIfCacheHit() throws Exception {

    Mockito.when(testCompilePhase.isCacheHit()).thenReturn(true);
    Mockito.when(buildCache.isTestExecutionCacheHit(session)).thenReturn(true);

    boolean expectDelegated = false;
    runEnabledTest("test", projectStatus, ExpectedTryCacheUse.TEST_EXECUTION, expectDelegated);

    Mockito.verify(testExecutionPhase, Mockito.times(1)).setCacheHit();
  }

  @Test
  public void testExecutionSecondNotDelegatedIfCacheHit() throws Exception {

    Mockito.when(testExecutionPhase.isCacheHit()).thenReturn(true);

    boolean expectDelegated = false;
    runEnabledTest("test", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);
  }

  //
  // IT test
  //

  @Test
  public void itTestExecutionDelegatedWhenNoTestCompileCacheHit() throws Exception {

    boolean expectDelegated = true;
    runEnabledTest("integration-test", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);

    Mockito.verify(itTestExecutionPhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void itTestExecutionDelegatedWhenSkipTests() throws Exception {

    Mockito.when(projectStatus.isSkipTests()).thenReturn(true);

    boolean expectDelegated = true;
    runEnabledTest("integration-test", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);

    Mockito.verify(itTestExecutionPhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void itTestExecutionDelegatedWhenNoCacheHit() throws Exception {

    Mockito.when(testCompilePhase.isCacheHit()).thenReturn(true);

    boolean expectDelegated = true;
    runEnabledTest("integration-test", projectStatus,
        ExpectedTryCacheUse.INTEGRATION_TEST_EXECUTION, expectDelegated);

    Mockito.verify(itTestExecutionPhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void itTestExecutionSecondDelegatedWhenNoCacheHit() throws Exception {

    Mockito.when(itTestExecutionPhase.isVisited()).thenReturn(true);
    Mockito.when(itTestExecutionPhase.notVisited()).thenReturn(false);

    boolean expectDelegated = true;
    runEnabledTest("integration-test", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);

    Mockito.verify(itTestExecutionPhase, Mockito.never()).setCacheHit();
  }

  @Test
  public void itTestExecutionFirstNotDelegatedIfCacheHit() throws Exception {

    Mockito.when(testCompilePhase.isCacheHit()).thenReturn(true);
    Mockito.when(buildCache.isIntegrationTestExecutionCacheHit(session)).thenReturn(true);

    boolean expectDelegated = false;
    runEnabledTest("integration-test", projectStatus,
        ExpectedTryCacheUse.INTEGRATION_TEST_EXECUTION, expectDelegated);

    Mockito.verify(itTestExecutionPhase, Mockito.times(1)).setCacheHit();
  }

  @Test
  public void itTestExecutionSecondNotDelegatedIfCacheHit() throws Exception {

    Mockito.when(itTestExecutionPhase.isCacheHit()).thenReturn(true);

    boolean expectDelegated = false;
    runEnabledTest("integration-test", projectStatus, ExpectedTryCacheUse.NONE, expectDelegated);
  }

  //
  // disabled
  //

  @Test
  public void testBuildCacheDisabledFully() throws Exception {

    MojoExecution mojoExecution = Mockito.mock(MojoExecution.class);
    Mockito.when(buildCache.isBuildCacheDisabled()).thenReturn(true);

    manager.executeMojo(session, mojoExecution);

    Mockito.verify(buildCache, Mockito.times(1)).isInitialized();
    Mockito.verify(buildCache, Mockito.times(1)).isInitializationError();
    Mockito.verify(buildCache, Mockito.times(1)).isBuildCacheDisabled();
    Mockito.verifyNoMoreInteractions(buildCache);

    Mockito.verify(delegate, Mockito.times(1)).executeMojo(session, mojoExecution);
    Mockito.verify(session, Mockito.times(1)).getCurrentProject();
    Mockito.verifyNoMoreInteractions(session);

    Mockito.verifyNoInteractions(project);
    Mockito.verifyNoInteractions(mojoExecution);
    Mockito.verifyNoInteractions(projectStatus);

    Mockito.verifyNoInteractions(mainCompilePhase);
    Mockito.verifyNoInteractions(testCompilePhase);
    Mockito.verifyNoInteractions(testExecutionPhase);
  }

  @Test
  public void testBuildCacheDisabledProject() throws Exception {

    MojoExecution mojoExecution = Mockito.mock(MojoExecution.class);

    projectStatus = Mockito.mock(ProjectBuildStatus.class);
    Mockito.when(projectStatus.isBuildCacheDisabled()).thenReturn(true);
    Mockito.when(buildCache.getProjectStatus(session, mojoExecution)).thenReturn(projectStatus);

    manager.executeMojo(session, mojoExecution);

    Mockito.verify(buildCache, Mockito.times(1)).isInitialized();
    Mockito.verify(buildCache, Mockito.times(1)).isInitializationError();
    Mockito.verify(buildCache, Mockito.times(1)).isBuildCacheDisabled();
    Mockito.verify(buildCache, Mockito.times(1)).getProjectStatus(session, mojoExecution);
    Mockito.verifyNoMoreInteractions(buildCache);

    Mockito.verify(projectStatus, Mockito.times(1)).isBuildCacheDisabled();
    Mockito.verifyNoMoreInteractions(projectStatus);

    Mockito.verify(delegate, Mockito.times(1)).executeMojo(session, mojoExecution);
    Mockito.verify(session, Mockito.times(1)).getCurrentProject();
    Mockito.verifyNoMoreInteractions(session);
    Mockito.verifyNoInteractions(project);
    Mockito.verifyNoInteractions(mojoExecution);

    Mockito.verifyNoInteractions(mainCompilePhase);
    Mockito.verifyNoInteractions(testCompilePhase);
    Mockito.verifyNoInteractions(testExecutionPhase);
  }

  @Test
  public void testIgnoredLifecyclePhase() throws Exception {

    MojoExecution mojoExecution = Mockito.mock(MojoExecution.class);
    Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn("validate");

    projectStatus = Mockito.mock(ProjectBuildStatus.class);
    Mockito.when(projectStatus.isBuildCacheEnabled()).thenReturn(true);
    Mockito.when(buildCache.getProjectStatus(session, mojoExecution)).thenReturn(projectStatus);

    manager.executeMojo(session, mojoExecution);

    Mockito.verify(buildCache, Mockito.times(1)).isInitialized();
    Mockito.verify(buildCache, Mockito.times(1)).isInitializationError();
    Mockito.verify(buildCache, Mockito.times(1)).isBuildCacheDisabled();
    Mockito.verify(buildCache, Mockito.times(1)).getProjectStatus(session, mojoExecution);
    Mockito.verifyNoMoreInteractions(buildCache);
    Mockito.verify(projectStatus, Mockito.times(1)).isBuildCacheDisabled();
    Mockito.verifyNoMoreInteractions(projectStatus);
    Mockito.verify(delegate, Mockito.times(1)).executeMojo(session, mojoExecution);
    Mockito.verify(session, Mockito.times(1)).getCurrentProject();
    Mockito.verifyNoMoreInteractions(session);
    Mockito.verifyNoInteractions(project);
    Mockito.verify(mojoExecution, Mockito.times(4)).getLifecyclePhase();
    Mockito.verifyNoMoreInteractions(mojoExecution);
  }

  private void runEnabledTest(String lifecyclePhase, ProjectBuildStatus projectStatus,
      ExpectedTryCacheUse expectedCacheUse, boolean expectDelegated) throws Exception {

    MojoExecution mojoExecution = Mockito.mock(MojoExecution.class);
    Mockito.when(mojoExecution.getLifecyclePhase()).thenReturn(lifecyclePhase);

    Mockito.when(buildCache.getProjectStatus(session, mojoExecution)).thenReturn(projectStatus);

    manager.executeMojo(session, mojoExecution);

    Mockito.verify(buildCache, Mockito.times(1)).isInitialized();
    Mockito.verify(buildCache, Mockito.times(1)).isInitializationError();
    Mockito.verify(buildCache, Mockito.times(1)).isBuildCacheDisabled();
    Mockito.verify(buildCache, Mockito.times(1)).getProjectStatus(session, mojoExecution);
    Mockito.verify(projectStatus, Mockito.times(1)).isBuildCacheDisabled();
    Mockito.verify(projectStatus, Mockito.times(1)).isMavenMainSkip();

    if (MojoExecUtil.isCompileRelatedPhase(mojoExecution)) {
      Mockito.verify(mainCompilePhase, Mockito.times(1)).setVisited();
      Mockito.verifyNoInteractions(testCompilePhase);
      Mockito.verifyNoInteractions(testExecutionPhase);
      Mockito.verifyNoInteractions(itTestExecutionPhase);
    }

    if (MojoExecUtil.isTestCompileRelatedPhase(mojoExecution) && !projectStatus.isMavenTestSkip()) {
      Mockito.verify(testCompilePhase, Mockito.times(1)).setVisited();
      Mockito.verifyNoInteractions(testExecutionPhase);
      Mockito.verifyNoInteractions(itTestExecutionPhase);

    } else if (MojoExecUtil.isTestCompileRelatedPhase(mojoExecution)) {
      Mockito.verifyNoInteractions(mainCompilePhase);
      Mockito.verifyNoInteractions(testCompilePhase);
      Mockito.verifyNoInteractions(testExecutionPhase);
      Mockito.verifyNoInteractions(itTestExecutionPhase);
    }

    if (MojoExecUtil.isTestRelatedPhase(mojoExecution) && !projectStatus.isSkipTests()) {
      Mockito.verify(testExecutionPhase, Mockito.times(1)).setVisited();
      Mockito.verifyNoInteractions(mainCompilePhase);
      Mockito.verifyNoInteractions(itTestExecutionPhase);

    } else if (MojoExecUtil.isTestRelatedPhase(mojoExecution)) {
      Mockito.verifyNoInteractions(mainCompilePhase);
      Mockito.verifyNoInteractions(testCompilePhase);
      Mockito.verifyNoInteractions(testExecutionPhase);
      Mockito.verifyNoInteractions(itTestExecutionPhase);
    }

    if (MojoExecUtil.isIntegrationTestRelatedPhase(mojoExecution) && !projectStatus.isSkipTests()
        && !projectStatus.isSkipItTests()) {
      Mockito.verify(itTestExecutionPhase, Mockito.times(1)).setVisited();
      Mockito.verifyNoInteractions(mainCompilePhase);
      Mockito.verifyNoInteractions(testExecutionPhase);

    } else if (MojoExecUtil.isIntegrationTestRelatedPhase(mojoExecution)) {
      Mockito.verifyNoInteractions(mainCompilePhase);
      Mockito.verifyNoInteractions(testCompilePhase);
      Mockito.verifyNoInteractions(testExecutionPhase);
      Mockito.verifyNoInteractions(itTestExecutionPhase);
    }

    if (ExpectedTryCacheUse.MAIN_CLASSES.equals(expectedCacheUse)) {
      Mockito.verify(buildCache, Mockito.times(1)).useCachedMainClasses(session);
    } else {
      Mockito.verify(buildCache, Mockito.never()).useCachedMainClasses(session);
    }

    if (ExpectedTryCacheUse.TEST_CLASSES.equals(expectedCacheUse)) {
      Mockito.verify(buildCache, Mockito.times(1)).useCachedTestClasses(session);
    } else {
      Mockito.verify(buildCache, Mockito.never()).useCachedTestClasses(session);
    }

    if (ExpectedTryCacheUse.TEST_EXECUTION.equals(expectedCacheUse)) {
      Mockito.verify(buildCache, Mockito.times(1)).isTestExecutionCacheHit(session);
    } else {
      Mockito.verify(buildCache, Mockito.never()).isTestExecutionCacheHit(session);
    }

    if (ExpectedTryCacheUse.INTEGRATION_TEST_EXECUTION.equals(expectedCacheUse)) {
      Mockito.verify(buildCache, Mockito.times(1)).isIntegrationTestExecutionCacheHit(session);
    } else {
      Mockito.verify(buildCache, Mockito.never()).isIntegrationTestExecutionCacheHit(session);
    }

    Mockito.verifyNoMoreInteractions(buildCache);

    if (expectDelegated) {
      Mockito.verify(delegate, Mockito.times(1)).executeMojo(session, mojoExecution);
    } else {
      Mockito.verify(delegate, Mockito.never()).executeMojo(session, mojoExecution);
    }
  }
}
