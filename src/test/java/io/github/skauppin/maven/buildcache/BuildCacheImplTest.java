package io.github.skauppin.maven.buildcache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.lifecycle.LifecycleNotFoundException;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.model.Build;
import org.apache.maven.model.FileSet;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import io.github.skauppin.maven.buildcache.BuildCacheImpl;
import io.github.skauppin.maven.buildcache.CacheCleanupExecutorImpl;
import io.github.skauppin.maven.buildcache.Configuration;
import io.github.skauppin.maven.buildcache.FileUtil;
import io.github.skauppin.maven.buildcache.ProjectBuildStatus;

public class BuildCacheImplTest {

  private MavenProject project;
  private MavenSession session;

  private PluginParameterExpressionEvaluator evaluator;
  private LifecycleExecutor lifecycleExecutor;
  private FileUtil fileUtil;
  private Configuration configuration;
  private CacheCleanupExecutorImpl fullCacheCleanupExecutor;
  private BuildCacheImpl buildCache;

  private MavenExecutionPlan mockMavenExecutionPlan;

  @BeforeEach
  public void init() throws Exception {
    project = Mockito.mock(MavenProject.class);
    session = Mockito.mock(MavenSession.class);
    evaluator = Mockito.mock(PluginParameterExpressionEvaluator.class);
    lifecycleExecutor = Mockito.mock(LifecycleExecutor.class);
    fileUtil = Mockito.mock(FileUtil.class);
    configuration = Mockito.mock(Configuration.class);
    fullCacheCleanupExecutor = Mockito.mock(CacheCleanupExecutorImpl.class);

    Build build = Mockito.mock(Build.class);
    Mockito.when(build.getDirectory()).thenReturn("/project/target");

    Mockito.when(project.getGroupId()).thenReturn("com.test");
    Mockito.when(project.getArtifactId()).thenReturn("test-lib");
    Mockito.when(project.getBuild()).thenReturn(build);
    Mockito.when(session.getCurrentProject()).thenReturn(project);
    Mockito.when(configuration.getCacheDirectory()).thenReturn("/home/user/.m2/buildcache");

    mockMavenExecutionPlan = HashUtilTest.mockMavenExecutionPlan();
    Mockito.when(lifecycleExecutor.calculateExecutionPlan(session, "verify"))
        .thenReturn(mockMavenExecutionPlan);

    buildCache = new BuildCacheImpl();
    buildCache.setExpressionEvaluatorProvider(s -> evaluator);
    buildCache.setLogger(Mockito.mock(Logger.class));
    buildCache.setLifecycleExecutor(lifecycleExecutor);
    buildCache.setFileUtil(fileUtil);
    buildCache.setConfiguration(configuration);
    buildCache.setCacheCleanupExecutor(fullCacheCleanupExecutor);
  }

  @Test
  public void testInitializeWithMissingConfiguration()
      throws IOException, ExpressionEvaluationException {

    Mockito.when(evaluator.evaluate("${" + BuildCacheImpl.USER_HOME + "}"))
        .thenReturn("/home/user");
    Mockito.when(evaluator.evaluate("${" + BuildCacheImpl.BUILD_CACHE_DEBUG + "}"))
        .thenReturn("true");
    Mockito.when(evaluator.evaluate("${" + BuildCacheImpl.BUILD_CACHE_PROFILE + "}"))
        .thenReturn("true");

    buildCache.initializeSession(session);

    assertTrue(buildCache.isInitialized());
    assertFalse(buildCache.isInitializationError());
    assertNull(buildCache.getInitializationErrorMessage());
    assertTrue(buildCache.isBuildCacheDebug());
    assertTrue(buildCache.isBuildCacheProfile());
    Mockito.verify(configuration).setCachingDefaults(Mockito.eq("/home/user/.m2/buildcache"));
    Mockito.verifyNoMoreInteractions(configuration);
    Mockito.verify(fullCacheCleanupExecutor).initialize(Mockito.any());
    Mockito.verifyNoMoreInteractions(fullCacheCleanupExecutor);
  }

  @Test
  public void testInitializeWithFullCacheCleanup()
      throws IOException, ExpressionEvaluationException {

    Mockito.when(evaluator.evaluate("${" + BuildCacheImpl.BUILD_CACHE_FULL_CLEAN + "}"))
        .thenReturn("true");

    buildCache.initializeSession(session);

    Mockito.verify(fullCacheCleanupExecutor).initialize(Mockito.any());
    Mockito.verify(fullCacheCleanupExecutor).fullCacheCleanup();
  }

  @Test
  public void testInitializeWithInvalidConfiguration()
      throws IOException, ParserConfigurationException, SAXException {
    Path tmpDir = prepareConfigurationFile(".mvn");

    Mockito.when(session.getExecutionRootDirectory()).thenReturn(tmpDir.toFile().getAbsolutePath());
    Mockito.doThrow(SAXParseException.class).when(configuration)
        .readProjectConfiguration(Mockito.any());

    buildCache.initializeSession(session);

    assertTrue(buildCache.isInitializationError());
    assertTrue(buildCache.getInitializationErrorMessage()
        .startsWith("buildcache initialization failed: Invalid config file"));
  }

  @Test
  public void testInitializeWithProjectConfiguration() throws Exception {

    Mockito.when(evaluator.evaluate("${" + BuildCacheImpl.USER_HOME + "}"))
        .thenReturn("/home/user");

    Path tmpDir = prepareConfigurationFile(".mvn");
    Mockito.when(session.getExecutionRootDirectory()).thenReturn(tmpDir.toFile().getAbsolutePath());

    buildCache.initializeSession(session);

    assertFalse(buildCache.isInitializationError());
    assertNull(buildCache.getInitializationErrorMessage());
    assertFalse(buildCache.isBuildCacheDebug());
    assertFalse(buildCache.isBuildCacheProfile());
    Mockito.verify(configuration).readProjectConfiguration(Mockito.any());
    Mockito.verify(configuration).setCachingDefaults(Mockito.eq("/home/user/.m2/buildcache"));
    Mockito.verifyNoMoreInteractions(configuration);
  }

  @Test
  public void testInitializeWithGlobalConfiguration() throws Exception {

    Path tmpDir = prepareConfigurationFile(".m2");
    Mockito.when(evaluator.evaluate("${" + BuildCacheImpl.USER_HOME + "}")).thenReturn(tmpDir);

    buildCache.initializeSession(session);

    assertFalse(buildCache.isInitializationError());
    assertNull(buildCache.getInitializationErrorMessage());
    Mockito.verify(configuration).readCacheConfiguration(Mockito.any(),
        Mockito.eq(String.format("%s/.m2/buildcache", tmpDir)));
    Mockito.verifyNoMoreInteractions(configuration);
  }

  private Path prepareConfigurationFile(String dir) throws IOException {
    Path tmpDir = Files.createTempDirectory("buildcache-maven-extension-");
    Path configDir = tmpDir.resolve(dir);
    configDir.toFile().mkdirs();
    Path configFile = configDir.resolve("buildcache.xml");
    Files.write(configFile, new byte[0]);
    return tmpDir;
  }

  @Test
  public void testCleanProject() throws IOException {
    File expectedProjectDir = new File("/home/user/.m2/buildcache/com/test/test-lib");
    buildCache.cleanProject(session);
    Mockito.verify(fullCacheCleanupExecutor).projectCacheCleanup(expectedProjectDir);
  }

  @Test
  public void testGetProjectStatus() throws ExpressionEvaluationException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    assertTrue(projectStatus.isBuildCacheEnabled());
    assertFalse(projectStatus.isMavenMainSkip());
    assertFalse(projectStatus.isMavenTestSkip());
    assertFalse(projectStatus.isSkipTests());
    assertTrue(buildCache.getProjectStatus("com.test", "test-lib").isPresent());
    assertEquals(mockMavenExecutionPlan, projectStatus.getMavenExecutionPlan());
  }

  @Test
  public void testGetProjectStatusWhenDisabled() throws ExpressionEvaluationException {
    Mockito.when(evaluator.evaluate("${" + BuildCacheImpl.MAVEN_MAIN_SKIP + "}"))
        .thenReturn("true");
    Mockito.when(evaluator.evaluate("${" + BuildCacheImpl.BUILD_CACHE_DISABLE + "}"))
        .thenReturn("true");

    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    assertFalse(projectStatus.isBuildCacheEnabled());
    assertTrue(projectStatus.isBuildCacheDisabled());
    assertTrue(projectStatus.isMavenMainSkip());
    assertFalse(projectStatus.isMavenTestSkip());
    assertFalse(projectStatus.isSkipTests());
    assertTrue(buildCache.getProjectStatus("com.test", "test-lib").isPresent());
    assertEquals(mockMavenExecutionPlan, projectStatus.getMavenExecutionPlan());
  }

  //
  // compile
  //

  @Test
  public void testGetProjectStatusInCompilePhaseWithoutConfiguration() throws Exception {
    testGetProjectStatusInCompilePhase("c13839d00f91bb51d8897b185e8ee2d5");
  }

  @Test
  public void testGetProjectStatusInCompilePhaseWithConfiguration() throws Exception {
    String fileSetDirectory = FileUtilTest.class.getResource("/").getFile();
    FileSet set = new FileSet();
    set.setDirectory(fileSetDirectory);
    set.addInclude("trigger.txt");
    Mockito.when(configuration.getMainCompileTriggers(project))
        .thenReturn(Collections.singletonList(set));

    testGetProjectStatusInCompilePhase("72f2c4ffee2c5247f466bfdd5d174d5f");
  }

  private void testGetProjectStatusInCompilePhase(String expectedHash) throws Exception {
    Set<Artifact> dependencies = HashUtilTest.mockDependencies();
    Mockito.when(project.getArtifacts()).thenReturn(dependencies);
    List<String> compileSourceRoots = Collections.singletonList(
        Paths.get(FileUtilTest.class.getResource("/test-files").getFile()).toString());
    Mockito.when(project.getCompileSourceRoots()).thenReturn(compileSourceRoots);

    MojoExecution mojoExecution = HashUtilTest.mockMojoExecution("compile");

    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session, mojoExecution);
    assertTrue(projectStatus.isBuildCacheEnabled());
    assertTrue(projectStatus.getMainCompile().isConfigured());
    assertEquals(expectedHash, projectStatus.getMainCompile().getPhaseHash());
  }

  @Test
  public void testGetProjectStatusInCompilePhase2ndTime() throws Exception {

    MojoExecution mojoInit = HashUtilTest.mockMojoExecution("initialize");
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session, mojoInit);
    assertTrue(projectStatus.isBuildCacheEnabled());

    projectStatus.getMainCompile().setConfigured(true);

    MojoExecution mojoCompile = HashUtilTest.mockMojoExecution("compile");
    projectStatus = buildCache.getProjectStatus(session, mojoCompile);
    assertTrue(projectStatus.isBuildCacheEnabled());
    assertNull(projectStatus.getMainCompile().getPhaseDetails());
    assertNull(projectStatus.getMainCompile().getPhaseHash());
  }

  @Test
  public void testGetProjectStatusInCompilePhaseException() throws Exception {

    Mockito.when(lifecycleExecutor.calculateExecutionPlan(session, "verify"))
        .thenThrow(LifecycleNotFoundException.class);

    MojoExecution mojoExecution =
        HashUtilTest.mockMojoExecution("compile", "com.test", "test", "v1.0.0", null);

    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session, mojoExecution);
    assertFalse(projectStatus.isBuildCacheEnabled());
    assertFalse(projectStatus.getMainCompile().isConfigured());
  }

  //
  // test-compile
  //

  @Test
  public void testGetProjectStatusInTestCompilePhase() throws Exception {

    MojoExecution mojoExecution = HashUtilTest.mockMojoExecution("initialize");
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session, mojoExecution);

    Set<Artifact> dependencies = HashUtilTest.mockDependencies();
    Mockito.when(project.getArtifacts()).thenReturn(dependencies);
    List<String> compileSourceRoots = Collections.singletonList(
        Paths.get(FileUtilTest.class.getResource("/test-files").getFile()).toString());
    Mockito.when(project.getTestCompileSourceRoots()).thenReturn(compileSourceRoots);

    mojoExecution = HashUtilTest.mockMojoExecution("test-compile");
    projectStatus = buildCache.getProjectStatus(session, mojoExecution);

    assertTrue(projectStatus.isBuildCacheEnabled());
    assertTrue(projectStatus.getTestCompile().isConfigured());
    assertEquals("4d4e13db154c8c55823f9af7afc9a91f", projectStatus.getTestCompile().getPhaseHash());
  }

  @Test
  public void testGetProjectStatusInTestCompilePhase2ndTime() throws Exception {

    MojoExecution mojoInit = HashUtilTest.mockMojoExecution("initialize");
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session, mojoInit);
    assertTrue(projectStatus.isBuildCacheEnabled());

    projectStatus.getTestCompile().setConfigured(true);

    MojoExecution mojoCompile = HashUtilTest.mockMojoExecution("test-compile");
    projectStatus = buildCache.getProjectStatus(session, mojoCompile);
    assertTrue(projectStatus.isBuildCacheEnabled());
    assertNull(projectStatus.getTestCompile().getPhaseDetails());
    assertNull(projectStatus.getTestCompile().getPhaseHash());
  }

  @Test
  public void testGetProjectStatusInTestCompilePhaseException() throws Exception {

    MojoExecution mojoExecution = HashUtilTest.mockMojoExecution("initialize");
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session, mojoExecution);
    projectStatus.setMavenExecutionPlan(Mockito.mock(MavenExecutionPlan.class));

    Mockito.when(project.getTestCompileSourceRoots()).thenThrow(RuntimeException.class);
    mojoExecution = HashUtilTest.mockMojoExecution("test-compile");
    projectStatus = buildCache.getProjectStatus(session, mojoExecution);

    assertFalse(projectStatus.isBuildCacheEnabled());
    assertFalse(projectStatus.getTestCompile().isConfigured());
    Mockito.verify(project, Mockito.times(1)).getTestCompileSourceRoots();
  }

  //
  // test
  //

  @Test
  public void testGetProjectStatusInTestPhase() throws Exception {
    testGetProjectStatusInTestPhase("test", s -> s.getTest(), "3f4dd7243c25a3ffd8a1a7fb2a81f5cd");
  }

  @Test
  public void testGetProjectStatusInIntegrationTestPhase() throws Exception {
    testGetProjectStatusInTestPhase("integration-test", s -> s.getIntegrationTest(),
        "c6e605c9af4e07a45bb38568c1dcdf0e");
  }

  private void testGetProjectStatusInTestPhase(String lifecyclePhase,
      Function<ProjectBuildStatus, ProjectBuildStatus.Phase> expectedConfiguredPhaseProvider,
      String expectedPhaseHash) throws Exception {

    MojoExecution mojoExecution = HashUtilTest.mockMojoExecution("initialize");
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session, mojoExecution);
    projectStatus.getMainCompile().setPhaseHash("xyz-111");
    projectStatus.getTestCompile().setPhaseHash("abcd-1234");

    String resourceRoot =
        Paths.get(FileUtilTest.class.getResource("/test-files").getFile()).toString();
    Resource mainResource = Mockito.mock(Resource.class);
    Mockito.when(mainResource.getDirectory()).thenReturn(resourceRoot);
    Mockito.when(mainResource.getIncludes()).thenReturn(Collections.emptyList());
    Mockito.when(mainResource.getExcludes()).thenReturn(Collections.emptyList());

    Mockito.when(project.getResources()).thenReturn(Collections.singletonList(mainResource));
    Mockito.when(project.getTestResources()).thenReturn(Collections.singletonList(mainResource));

    mojoExecution = HashUtilTest.mockMojoExecution(lifecyclePhase);
    projectStatus = buildCache.getProjectStatus(session, mojoExecution);

    assertTrue(projectStatus.isBuildCacheEnabled());
    assertTrue(expectedConfiguredPhaseProvider.apply(projectStatus).isConfigured());
    assertEquals(expectedPhaseHash,
        expectedConfiguredPhaseProvider.apply(projectStatus).getPhaseHash());
  }

  @Test
  public void testGetProjectStatusInTestPhase2ndTime() throws Exception {

    MojoExecution mojoInit = HashUtilTest.mockMojoExecution("initialize");
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session, mojoInit);
    assertTrue(projectStatus.isBuildCacheEnabled());

    projectStatus.getTest().setConfigured(true);

    MojoExecution mojoCompile = HashUtilTest.mockMojoExecution("test");
    projectStatus = buildCache.getProjectStatus(session, mojoCompile);
    assertTrue(projectStatus.isBuildCacheEnabled());
    assertNull(projectStatus.getTest().getPhaseDetails());
    assertNull(projectStatus.getTest().getPhaseHash());
  }

  @Test
  public void testGetProjectStatusInTestPhaseException() throws Exception {

    MojoExecution mojoExecution = HashUtilTest.mockMojoExecution("initialize");
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session, mojoExecution);

    Mockito.when(project.getResources()).thenThrow(RuntimeException.class);
    mojoExecution = HashUtilTest.mockMojoExecution("test");
    projectStatus = buildCache.getProjectStatus(session, mojoExecution);

    assertFalse(projectStatus.isBuildCacheEnabled());
    assertFalse(projectStatus.getTest().isConfigured());
    Mockito.verify(project, Mockito.times(1)).getResources();
  }

  @Test
  public void testGetProjectStatusWithSessionExpressionEvaluationException()
      throws ExpressionEvaluationException {

    Mockito.when(evaluator.evaluate(Mockito.anyString()))
        .thenThrow(ExpressionEvaluationException.class);

    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    assertFalse(projectStatus.isBuildCacheEnabled());
    assertTrue(buildCache.getProjectStatus("com.test", "test-lib").isPresent());
  }

  @Test
  public void testGetProjectStatusWithGroupAndArtifactId() throws ExpressionEvaluationException {
    Optional<ProjectBuildStatus> projectStatus = buildCache.getProjectStatus("com.test", "test");
    assertFalse(projectStatus.isPresent());
  }

  @Test
  public void testUseCachedMainClassesWhenCacheHit() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getMainCompile().setPhaseHash("abcd-1234");

    Path zipFile = Paths.get("/home/user/.m2/buildcache/com/test/test-lib/classes-abcd-1234.zip");
    Mockito.when(fileUtil.fileExists(zipFile)).thenReturn(true);

    boolean cacheHit = buildCache.useCachedMainClasses(session);
    assertTrue(cacheHit);

    Mockito.verify(fileUtil, Mockito.times(1)).fileExists(zipFile);
    Path classesDir = Paths.get("/project/target/classes");
    Mockito.verify(fileUtil, Mockito.times(1)).mkdirs(classesDir);
    Mockito.verify(fileUtil, Mockito.times(1)).unzip(zipFile, classesDir);
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testUseCachedMainClassesWhenCacheHitAndIgnore()
      throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getMainCompile().setPhaseHash("abcd-1234");

    buildCache.setBuildCacheIgnore(true);

    Path zipFile = Paths.get("/home/user/.m2/buildcache/com/test/test-lib/classes-abcd-1234.zip");
    Mockito.when(fileUtil.fileExists(zipFile)).thenReturn(true);

    boolean cacheHit = buildCache.useCachedMainClasses(session);
    assertFalse(cacheHit);

    Mockito.verify(fileUtil, Mockito.times(1)).fileExists(zipFile);
    Mockito.verify(fileUtil, Mockito.times(1)).deleteFile(zipFile);
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testUseCachedMainClassesWhenNoCacheHit() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getMainCompile().setPhaseHash("abcd-1234");

    Path zipFile = Paths.get("/home/user/.m2/buildcache/com/test/test-lib/classes-abcd-1234.zip");
    Mockito.when(fileUtil.fileExists(zipFile)).thenReturn(false);

    boolean cacheHit = buildCache.useCachedMainClasses(session);
    assertFalse(cacheHit);

    Mockito.verify(fileUtil, Mockito.times(1)).fileExists(zipFile);
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testUseCachedMainClassesWhenException() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getMainCompile().setPhaseHash("abcd-1234");

    Path zipFile = Paths.get("/home/user/.m2/buildcache/com/test/test-lib/classes-abcd-1234.zip");
    Path classesDir = Paths.get("/project/target/classes");
    Mockito.when(fileUtil.fileExists(zipFile)).thenReturn(true);
    Mockito.when(fileUtil.unzip(zipFile, classesDir)).thenThrow(IOException.class);

    boolean cacheHit = buildCache.useCachedMainClasses(session);
    assertFalse(cacheHit);

    Mockito.verify(fileUtil, Mockito.times(1)).fileExists(zipFile);
    Mockito.verify(fileUtil, Mockito.times(1)).mkdirs(classesDir);
    Mockito.verify(fileUtil, Mockito.times(1)).unzip(zipFile, classesDir);
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testUseCachedTestClassesWhenCacheHit() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getTestCompile().setPhaseHash("abcd-1234");

    Path zipFile =
        Paths.get("/home/user/.m2/buildcache/com/test/test-lib/test-classes-abcd-1234.zip");
    Mockito.when(fileUtil.fileExists(zipFile)).thenReturn(true);

    boolean cacheHit = buildCache.useCachedTestClasses(session);
    assertTrue(cacheHit);

    Mockito.verify(fileUtil, Mockito.times(1)).fileExists(zipFile);
    Path classesDir = Paths.get("/project/target/test-classes");
    Mockito.verify(fileUtil, Mockito.times(1)).mkdirs(classesDir);
    Mockito.verify(fileUtil, Mockito.times(1)).unzip(zipFile, classesDir);
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testIsTestExecutionCacheHitWhenCacheHit() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getTest().setPhaseHash("abcd-1234");

    Path testCacheFile = Paths.get("/home/user/.m2/buildcache/com/test/test-lib/test-abcd-1234.ok");
    Mockito.when(fileUtil.fileExists(testCacheFile)).thenReturn(true);

    assertTrue(buildCache.isTestExecutionCacheHit(session));
  }

  @Test
  public void testIsTestExecutionCacheHitWhenCacheHitAndIgnore()
      throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getTest().setPhaseHash("abcd-1234");

    buildCache.setBuildCacheIgnore(true);

    Path testCacheFile = Paths.get("/home/user/.m2/buildcache/com/test/test-lib/test-abcd-1234.ok");
    Mockito.when(fileUtil.fileExists(testCacheFile)).thenReturn(true);

    assertFalse(buildCache.isTestExecutionCacheHit(session));

    Mockito.verify(fileUtil, Mockito.times(1)).deleteFile(testCacheFile);
  }

  @Test
  public void testIsTestExecutionCacheHitWhenNoCacheHit()
      throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getTest().setPhaseHash("abcd-1234");

    Path testCacheFile = Paths.get("/home/user/.m2/.mvn/buildcache/test/abcd-1234-test.ok");
    Mockito.when(fileUtil.fileExists(testCacheFile)).thenReturn(false);

    assertFalse(buildCache.isTestExecutionCacheHit(session));
  }

  @Test
  public void testIsIntegrationTestExecutionCacheHitWhenCacheHit()
      throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getIntegrationTest().setPhaseHash("abcd-1234");

    Path testCacheFile =
        Paths.get("/home/user/.m2/buildcache/com/test/test-lib/it-test-abcd-1234.ok");
    Mockito.when(fileUtil.fileExists(testCacheFile)).thenReturn(true);

    assertTrue(buildCache.isIntegrationTestExecutionCacheHit(session));
  }

  @Test
  public void testCacheMainClasses() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getMainCompile().setPhaseHash("abcd-1234");
    projectStatus.getMainCompile().setPhaseDetails("abcd");
    Path targetClasses = Paths.get("/project/target/classes");
    String[] classFiles = new String[] {"test.class"};
    Mockito.when(fileUtil.scanClassFilesFromDirectory(targetClasses)).thenReturn(classFiles);

    buildCache.cacheMainClasses(session);

    Path zipFile = Paths.get("/home/user/.m2/buildcache/com/test/test-lib/classes-abcd-1234.zip");
    Mockito.verify(fileUtil, Mockito.times(1)).scanClassFilesFromDirectory(targetClasses);
    Mockito.verify(fileUtil, Mockito.times(1)).createZipFile(zipFile, targetClasses, classFiles);
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testCacheMainClassesException() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getMainCompile().setPhaseHash("abcd-1234");
    Path targetClasses = Paths.get("/project/target/classes");
    String[] classFiles = new String[] {"test.class"};
    Mockito.when(fileUtil.scanClassFilesFromDirectory(targetClasses)).thenReturn(classFiles);
    Mockito.when(fileUtil.createZipFile(Mockito.any(), Mockito.any(), Mockito.any()))
        .thenThrow(IOException.class);

    buildCache.cacheMainClasses(session);

    Path zipFile = Paths.get("/home/user/.m2/buildcache/com/test/test-lib/classes-abcd-1234.zip");
    Mockito.verify(fileUtil, Mockito.times(1)).deleteFile(zipFile);
  }

  @Test
  public void testCacheTestClasses() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getTestCompile().setPhaseHash("abcd-1234");
    projectStatus.getTestCompile().setPhaseDetails("abcd");
    Path targetTestClasses = Paths.get("/project/target/test-classes");
    String[] classFiles = new String[] {"test.class"};
    Mockito.when(fileUtil.scanClassFilesFromDirectory(targetTestClasses)).thenReturn(classFiles);

    buildCache.cacheTestClasses(session);

    Path zipFile =
        Paths.get("/home/user/.m2/buildcache/com/test/test-lib/test-classes-abcd-1234.zip");
    Mockito.verify(fileUtil, Mockito.times(1)).scanClassFilesFromDirectory(targetTestClasses);
    Mockito.verify(fileUtil, Mockito.times(1)).createZipFile(zipFile, targetTestClasses,
        classFiles);
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testCacheTestExecution() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getTest().setPhaseHash("abcd-1234");

    buildCache.cacheTestExecution(session);

    Path testSuccessCacheFile =
        Paths.get("/home/user/.m2/buildcache/com/test/test-lib/test-abcd-1234.ok");
    Mockito.verify(fileUtil, Mockito.times(1)).writeFile(testSuccessCacheFile, "");
  }

  @Test
  public void testCacheIntegrationTestExecution() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getIntegrationTest().setPhaseHash("abcd-1234");

    buildCache.cacheIntegrationTestExecution(session);

    Path testSuccessCacheFile =
        Paths.get("/home/user/.m2/buildcache/com/test/test-lib/it-test-abcd-1234.ok");
    Mockito.verify(fileUtil, Mockito.times(1)).writeFile(testSuccessCacheFile, "");
  }

  @Test
  public void testWriteMainCompileDetails() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getMainCompile().setPhaseHash("abcd-1234");
    projectStatus.getMainCompile().setPhaseDetails("foo");

    buildCache.writeMainCompileDetails(session);

    Path testSuccessCacheFile =
        Paths.get("/home/user/.m2/buildcache/com/test/test-lib/classes-abcd-1234.txt");
    Mockito.verify(fileUtil, Mockito.times(1)).writeFile(testSuccessCacheFile, "foo");
  }

  @Test
  public void testWriteTestCompileDetails() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getTestCompile().setPhaseHash("abcd-1234");
    projectStatus.getTestCompile().setPhaseDetails("foo");

    buildCache.writeTestCompileDetails(session);

    Path testSuccessCacheFile =
        Paths.get("/home/user/.m2/buildcache/com/test/test-lib/test-classes-abcd-1234.txt");
    Mockito.verify(fileUtil, Mockito.times(1)).writeFile(testSuccessCacheFile, "foo");
  }

  @Test
  public void testWriteTestExecutionDetails() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getTest().setPhaseHash("abcd-1234");
    projectStatus.getTest().setPhaseDetails("foo");

    buildCache.writeTestExecutionDetails(session);

    Path testSuccessCacheFile =
        Paths.get("/home/user/.m2/buildcache/com/test/test-lib/test-abcd-1234.txt");
    Mockito.verify(fileUtil, Mockito.times(1)).writeFile(testSuccessCacheFile, "foo");
  }

  @Test
  public void testWriteIntegrationTestExecutionDetails() throws FileNotFoundException, IOException {
    ProjectBuildStatus projectStatus = buildCache.getProjectStatus(session);
    projectStatus.getIntegrationTest().setPhaseHash("abcd-1234");
    projectStatus.getIntegrationTest().setPhaseDetails("foo");

    buildCache.writeIntegrationTestExecutionDetails(session);

    Path testSuccessCacheFile =
        Paths.get("/home/user/.m2/buildcache/com/test/test-lib/it-test-abcd-1234.txt");
    Mockito.verify(fileUtil, Mockito.times(1)).writeFile(testSuccessCacheFile, "foo");
  }

  @Test
  public void testNonEmptyPropertyValue_null() throws ExpressionEvaluationException {
    Mockito.when(evaluator.evaluate(Mockito.anyString())).thenReturn(null);
    assertFalse(BuildCacheImpl.nonEmptyPropertyValue(evaluator, "test"));
  }

  @Test
  public void testNonEmptyPropertyValue_empty() throws ExpressionEvaluationException {
    Mockito.when(evaluator.evaluate(Mockito.anyString())).thenReturn("");
    assertFalse(BuildCacheImpl.nonEmptyPropertyValue(evaluator, "test"));
  }

  @Test
  public void testNonEmptyPropertyValue_value() throws ExpressionEvaluationException {
    Mockito.when(evaluator.evaluate(Mockito.anyString())).thenReturn("X");
    assertTrue(BuildCacheImpl.nonEmptyPropertyValue(evaluator, "test"));
  }

  @Test
  public void testCheckProperty_null() throws ExpressionEvaluationException {
    Mockito.when(evaluator.evaluate(Mockito.anyString())).thenReturn(null);
    assertEquals(false, BuildCacheImpl.checkProperty(evaluator, "property"));
  }

  @Test
  public void testCheckProperty_true() throws ExpressionEvaluationException {
    Mockito.when(evaluator.evaluate(Mockito.anyString())).thenReturn("True");
    assertEquals(true, BuildCacheImpl.checkProperty(evaluator, "property"));
  }

  @Test
  public void testCheckProperty_notTrue() throws ExpressionEvaluationException {
    Mockito.when(evaluator.evaluate(Mockito.anyString())).thenReturn("foo");
    assertEquals(false, BuildCacheImpl.checkProperty(evaluator, "property"));
  }

}
