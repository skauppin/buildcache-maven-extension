package io.github.skauppin.maven.buildcache;

import java.util.Optional;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionEvent.Type;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class BuildCacheEventSpyTest {

  private BuildCacheEventSpy buildCacheEventSpy;

  private BuildCache buildCache;
  private ExecutionTimeRegister executionTimeRegister;
  private Logger logger;

  private ExecutionEvent executionEvent;
  private MavenExecutionResult mavenExecutionResult;

  private MavenSession session;
  private MavenProject project;

  private ProjectBuildStatus projectStatus;

  @BeforeEach
  public void initTest() {
    project = Mockito.mock(MavenProject.class);
    Mockito.when(project.getGroupId()).thenReturn("group");
    Mockito.when(project.getArtifactId()).thenReturn("artifact");

    session = Mockito.mock(MavenSession.class);
    Mockito.when(session.getCurrentProject()).thenReturn(project);

    executionEvent = Mockito.mock(ExecutionEvent.class);
    Mockito.when(executionEvent.getType()).thenReturn(Type.ProjectSucceeded);
    Mockito.when(executionEvent.getSession()).thenReturn(session);

    mavenExecutionResult = Mockito.mock(MavenExecutionResult.class);
    Mockito.when(mavenExecutionResult.getProject()).thenReturn(project);

    projectStatus = new ProjectBuildStatus();

    buildCache = Mockito.mock(BuildCache.class);
    Mockito.when(buildCache.getProjectStatus(Mockito.eq(session))).thenReturn(projectStatus);
    Mockito.when(buildCache.getProjectStatus(Mockito.eq("group"), Mockito.eq("artifact")))
        .thenReturn(Optional.of(projectStatus));

    executionTimeRegister = Mockito.mock(ExecutionTimeRegister.class);
    logger = Mockito.mock(Logger.class);

    buildCacheEventSpy = new BuildCacheEventSpy();
    buildCacheEventSpy.setBuildCache(buildCache);
    buildCacheEventSpy.setExecutionTimeRegister(executionTimeRegister);
    buildCacheEventSpy.setLogger(logger);
  }

  @Test
  public void testInitializeBuildCache() throws Exception {
    Mockito.when(executionEvent.getType()).thenReturn(Type.SessionStarted);

    buildCacheEventSpy.onEvent(executionEvent);

    Mockito.verify(buildCache, Mockito.times(1)).initializeSession(session);
    Mockito.verifyNoMoreInteractions(buildCache);
  }

  @Test
  public void testCleanProjectOnProjectStarted() throws Exception {
    Mockito.when(buildCache.isInitialized()).thenReturn(true);
    Mockito.when(executionEvent.getType()).thenReturn(Type.ProjectStarted);

    buildCacheEventSpy.onEvent(executionEvent);

    Mockito.verify(buildCache, Mockito.times(1)).isInitialized();
    Mockito.verify(buildCache, Mockito.times(1)).cleanProject(session);
    Mockito.verifyNoMoreInteractions(buildCache);
  }

  @Test
  public void testProjectSucceededWhenNoCacheHit() throws Exception {
    projectStatus.getMainCompile().setVisited();
    projectStatus.getTestCompile().setVisited();
    projectStatus.getTest().setVisited();
    projectStatus.getIntegrationTest().setVisited();

    buildCacheEventSpy.onEvent(executionEvent);

    Mockito.verify(logger, Mockito.times(4)).info(Mockito.anyString());
    Mockito.verify(buildCache, Mockito.times(1)).cacheMainClasses(session);
    Mockito.verify(buildCache, Mockito.times(1)).cacheTestClasses(session);
    Mockito.verify(buildCache, Mockito.times(1)).cacheTestExecution(session);
    Mockito.verify(buildCache, Mockito.times(1)).cacheIntegrationTestExecution(session);
  }

  @Test
  public void testProjectSucceededWhenNoCacheHitAndDebug() throws Exception {

    Mockito.when(buildCache.isBuildCacheDebug()).thenReturn(true);

    projectStatus.getMainCompile().setVisited();
    projectStatus.getTestCompile().setVisited();
    projectStatus.getTest().setVisited();
    projectStatus.getIntegrationTest().setVisited();

    buildCacheEventSpy.onEvent(executionEvent);

    Mockito.verify(logger, Mockito.times(5)).info(Mockito.anyString());
    Mockito.verify(buildCache, Mockito.times(1)).getProjectStatus(session);
    Mockito.verify(buildCache, Mockito.times(1)).cacheMainClasses(session);
    Mockito.verify(buildCache, Mockito.times(1)).cacheTestClasses(session);
    Mockito.verify(buildCache, Mockito.times(1)).cacheTestExecution(session);
    Mockito.verify(buildCache, Mockito.times(1)).cacheIntegrationTestExecution(session);
    Mockito.verify(buildCache, Mockito.times(1)).writeMainCompileDetails(session);
    Mockito.verify(buildCache, Mockito.times(1)).writeTestCompileDetails(session);
    Mockito.verify(buildCache, Mockito.times(1)).writeTestExecutionDetails(session);
    Mockito.verify(buildCache, Mockito.times(1)).writeIntegrationTestExecutionDetails(session);
    Mockito.verify(buildCache, Mockito.times(1)).isBuildCacheDebug();
    Mockito.verifyNoMoreInteractions(buildCache);
  }

  @Test
  public void testProjectSucceededWhenCacheHit() throws Exception {
    projectStatus.getMainCompile().setVisited();
    projectStatus.getMainCompile().setCacheHit();
    projectStatus.getTestCompile().setVisited();
    projectStatus.getTestCompile().setCacheHit();
    projectStatus.getTest().setVisited();
    projectStatus.getTest().setCacheHit();
    projectStatus.getIntegrationTest().setVisited();
    projectStatus.getIntegrationTest().setCacheHit();

    buildCacheEventSpy.onEvent(executionEvent);

    Mockito.verifyNoInteractions(logger);
    Mockito.verify(buildCache, Mockito.times(1)).getProjectStatus(session);
    Mockito.verify(buildCache, Mockito.times(1)).isBuildCacheDebug();
    Mockito.verifyNoMoreInteractions(buildCache);
  }

  @Test
  public void testProjectSucceededWhenNoCompileOrTest() throws Exception {

    buildCacheEventSpy.onEvent(executionEvent);

    Mockito.verifyNoInteractions(logger);
    Mockito.verify(buildCache, Mockito.times(1)).getProjectStatus(session);
    Mockito.verify(buildCache, Mockito.times(1)).isBuildCacheDebug();
    Mockito.verifyNoMoreInteractions(buildCache);
  }

  @Test
  public void testProjectFailed() throws Exception {

    Mockito.when(executionEvent.getType()).thenReturn(Type.ProjectFailed);

    buildCacheEventSpy.onEvent(executionEvent);

    Mockito.verifyNoInteractions(logger);
    Mockito.verifyNoInteractions(buildCache);
  }

  @Test
  public void testMavenExecutionResult() throws Exception {

    buildCacheEventSpy.onEvent(mavenExecutionResult);

    Mockito.verifyNoInteractions(executionTimeRegister);
    Mockito.verify(buildCache).isBuildCacheProfile();
    Mockito.verifyNoMoreInteractions(buildCache);
    Mockito.verifyNoInteractions(logger);
  }

  @Test
  public void testMavenExecutionResultWhenProfile() throws Exception {
    Mockito.when(buildCache.isBuildCacheProfile()).thenReturn(true);

    buildCacheEventSpy.onEvent(mavenExecutionResult);

    Mockito.verify(executionTimeRegister, Mockito.times(1)).logExecutionTimes();
    Mockito.verify(buildCache).isBuildCacheProfile();
    Mockito.verifyNoMoreInteractions(buildCache);
    Mockito.verifyNoInteractions(logger);
  }

  @Test
  public void testObjectEvent() throws Exception {

    buildCacheEventSpy.onEvent(new Object());

    Mockito.verifyNoInteractions(logger);
    Mockito.verifyNoInteractions(buildCache);
  }
}
