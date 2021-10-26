package io.github.skauppin.maven.buildcache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.codehaus.plexus.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class HashUtilTest {

  // @formatter:off
  public static String[] TEXT_FILES = {
      "test.txt",
      "test1/test1.txt",
      "test1/test2/test2.txt"
  };
  public static final String TEXT_FILES_SOURCE_HASHES =
      "test.txt:d8e8fca2dc0f896fd7cb4cb0031ba249\n"
      + "test1/test1.txt:3e7705498e8be60520841409ebc69bc1\n"
      + "test1/test2/test2.txt:126a8a51b9d1bbd07fddc65819a542c3\n";

  public static final String TRIGGER_TXT =
      "trigger.txt:6fc8ad2d58a14c64feca87d90df4bf71\n";

  public static final String DEPENDENCY_LIST =
      "com.foo:test-lib-x:2.0.0\n"
      + "com.test:test-lib-a:1.0.1\n"
      + "com.test:test-lib-b:1.0.0\n";

  public static final String COMPILE_PLUGIN_DETAILS =
      "compile com.test:test-plugin1:v1.0.0\n"
      + "abc\n"
      + "123\n"
      + "process-classes com.test:test-plugin2:v2.0.0\n"
      + "<test/>\n";
  public static final String TEST_COMPILE_PLUGIN_DETAILS =
      "test-compile com.test:test-plugin:v1.0.1\n\n";
  public static final String TEST_PLUGIN_DETAILS =
      "test com.test:test-plugin3:v3.0.0\n";
  public static final String INTEGRATION_TEST_PLUGIN_DETAILS =
      "integration-test com.test:test-plugin4:v4.0.0\n";
  // @formatter:on

  private HashUtil hashUtil;

  @BeforeEach
  public void init() throws Exception {
    Logger logger = Mockito.mock(Logger.class);
    hashUtil = new HashUtil();
    hashUtil.setLogger(logger);
  }

  @Test
  public void testSetProjectCompilePhaseDetails() throws IOException {
    String expectedPhaseDetails = "--- sources\n" + TEXT_FILES_SOURCE_HASHES + "--- dependencies\n"
        + DEPENDENCY_LIST + "--- plugins\n" + COMPILE_PLUGIN_DETAILS;
    String expectedPhaseHash = "c13839d00f91bb51d8897b185e8ee2d5";

    testSetProjectCompilePhaseDetails(Collections.emptyList(), Collections.emptyMap(),
        expectedPhaseDetails, expectedPhaseHash);
  }

  @Test
  public void testSetProjectCompilePhaseDetailsWithAdditionalFileSetsAndProperties()
      throws IOException {
    FileSet additionalFileSet = new FileSet();
    additionalFileSet.setDirectory(FileUtilTest.class.getResource("/").getFile());
    additionalFileSet.addInclude("trigger.txt");

    String expectedPhaseDetails = "--- sources\n" + TEXT_FILES_SOURCE_HASHES
        + "--- additional-triggers\n" + TRIGGER_TXT + "--- dependencies\n" + DEPENDENCY_LIST
        + "--- properties\nkey:value\n" + "--- plugins\n" + COMPILE_PLUGIN_DETAILS;
    String expectedPhaseHash = "202c9de1a36e1e9d5da28c801a5cde18";

    testSetProjectCompilePhaseDetails(Collections.singletonList(additionalFileSet),
        Collections.singletonMap("key", "value"), expectedPhaseDetails, expectedPhaseHash);
  }

  private void testSetProjectCompilePhaseDetails(List<FileSet> additionalFileSets,
      Map<String, String> properties, String expectedPhaseDetails, String expectedPhaseHash)
      throws IOException {

    ProjectBuildStatus projectStatus = new ProjectBuildStatus();
    projectStatus.setMavenExecutionPlan(mockMavenExecutionPlan());
    BuildCache buildCache = Mockito.mock(BuildCache.class);
    Mockito.when(buildCache.getProjectStatus(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(Optional.empty());

    String compileSourceRoot =
        Paths.get(FileUtilTest.class.getResource("/test-files").getFile()).toString();

    MavenProject project = Mockito.mock(MavenProject.class);
    Mockito.when(project.getCompileSourceRoots())
        .thenReturn(Collections.singletonList(compileSourceRoot));
    Set<Artifact> dependencies = mockDependencies();
    Mockito.when(project.getArtifacts()).thenReturn(dependencies);

    hashUtil.setProjectCompilePhaseDetails(projectStatus, buildCache, project, additionalFileSets,
        properties);

    assertEquals(expectedPhaseDetails, projectStatus.getMainCompile().getPhaseDetails());
    assertEquals(expectedPhaseHash, projectStatus.getMainCompile().getPhaseHash());
  }

  @Test
  public void testSetProjectTestCompilePhaseDetails() throws IOException {
    String expectedPhaseDetails = "--- sources\n" + TEXT_FILES_SOURCE_HASHES + "--- dependencies\n"
        + DEPENDENCY_LIST + "--- plugins\n" + TEST_COMPILE_PLUGIN_DETAILS;
    String expectedPhaseHash = "4d4e13db154c8c55823f9af7afc9a91f";
    testSetProjectTestCompilePhaseDetails(Collections.emptyList(), Collections.emptyMap(),
        expectedPhaseDetails, expectedPhaseHash);
  }

  @Test
  public void testSetProjectTestCompilePhaseDetailsWithAdditionalFileSetsAndProperties()
      throws IOException {
    FileSet additionalFileSet = new FileSet();
    additionalFileSet.setDirectory(FileUtilTest.class.getResource("/").getFile());
    additionalFileSet.addInclude("trigger.txt");

    String expectedPhaseDetails = "--- sources\n" + TEXT_FILES_SOURCE_HASHES
        + "--- additional-triggers\n" + TRIGGER_TXT + "--- dependencies\n" + DEPENDENCY_LIST
        + "--- properties\nkey:value\n" + "--- plugins\n" + TEST_COMPILE_PLUGIN_DETAILS;
    String expectedPhaseHash = "346ac217b1d3f9020c52e1b88461bd75";

    testSetProjectTestCompilePhaseDetails(Collections.singletonList(additionalFileSet),
        Collections.singletonMap("key", "value"), expectedPhaseDetails, expectedPhaseHash);
  }

  private void testSetProjectTestCompilePhaseDetails(List<FileSet> additionalFileSets,
      Map<String, String> properties, String expectedPhaseDetails, String expectedPhaseHash)
      throws IOException {

    ProjectBuildStatus projectStatus = new ProjectBuildStatus();
    projectStatus.setMavenExecutionPlan(mockMavenExecutionPlan());
    BuildCache buildCache = Mockito.mock(BuildCache.class);
    Mockito.when(buildCache.getProjectStatus(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(Optional.empty());

    String compileSourceRoot =
        Paths.get(FileUtilTest.class.getResource("/test-files").getFile()).toString();

    MavenProject project = Mockito.mock(MavenProject.class);
    Mockito.when(project.getTestCompileSourceRoots())
        .thenReturn(Collections.singletonList(compileSourceRoot));
    Mockito.when(project.getGroupId()).thenReturn("com.test");
    Mockito.when(project.getArtifactId()).thenReturn("test-lib");
    Set<Artifact> dependencies = mockDependencies();
    Mockito.when(project.getArtifacts()).thenReturn(dependencies);

    hashUtil.setProjectTestCompilePhaseDetails(projectStatus, buildCache, project,
        additionalFileSets, properties);

    assertEquals(expectedPhaseDetails, projectStatus.getTestCompile().getPhaseDetails());
    assertEquals(expectedPhaseHash, projectStatus.getTestCompile().getPhaseHash());
  }

  @Test
  public void testSetProjectTestPhaseDetails() throws IOException {
    String expectedPhaseDetails = "--- class-dependency\n" + "com.test:test-lib:classes-xyz-111\n"
        + "com.test:test-lib:test-classes-abcd-1234\n" + "--- resources\n"
        + TEXT_FILES_SOURCE_HASHES + "--- test-resources\n" + TEXT_FILES_SOURCE_HASHES
        + "--- plugins\n" + TEST_PLUGIN_DETAILS;
    String expectedPhaseHash = "3f4dd7243c25a3ffd8a1a7fb2a81f5cd";

    testSetProjectTestPhaseDetails(Collections.emptyList(), expectedPhaseDetails,
        expectedPhaseHash);
  }

  @Test
  public void testSetProjectTestPhaseDetailsWithAdditionalFileSets() throws IOException {
    FileSet additionalFileSet = new FileSet();
    additionalFileSet.setDirectory(FileUtilTest.class.getResource("/").getFile());
    additionalFileSet.addInclude("trigger.txt");

    String expectedPhaseDetails = "--- class-dependency\n" + "com.test:test-lib:classes-xyz-111\n"
        + "com.test:test-lib:test-classes-abcd-1234\n" + "--- resources\n"
        + TEXT_FILES_SOURCE_HASHES + "--- test-resources\n" + TEXT_FILES_SOURCE_HASHES
        + "--- additional-triggers\n" + TRIGGER_TXT + "--- plugins\n" + TEST_PLUGIN_DETAILS;
    String expectedPhaseHash = "152c7641fa743a0f0d1d0d5e70e7e7d0";

    testSetProjectTestPhaseDetails(Collections.singletonList(additionalFileSet),
        expectedPhaseDetails, expectedPhaseHash);
  }

  private void testSetProjectTestPhaseDetails(List<FileSet> additionalFileSets,
      String expectedPhaseDetails, String expectedPhaseHash) throws IOException {
    ProjectBuildStatus projectStatus = new ProjectBuildStatus();
    projectStatus.setMavenExecutionPlan(mockMavenExecutionPlan());
    projectStatus.getMainCompile().setPhaseHash("xyz-111");
    projectStatus.getTestCompile().setPhaseHash("abcd-1234");

    String resourceRoot =
        Paths.get(FileUtilTest.class.getResource("/test-files").getFile()).toString();

    Resource mainResource = Mockito.mock(Resource.class);
    Mockito.when(mainResource.getDirectory()).thenReturn(resourceRoot);
    Mockito.when(mainResource.getIncludes()).thenReturn(Collections.emptyList());
    Mockito.when(mainResource.getExcludes()).thenReturn(Collections.emptyList());

    MavenProject project = Mockito.mock(MavenProject.class);
    Mockito.when(project.getGroupId()).thenReturn("com.test");
    Mockito.when(project.getArtifactId()).thenReturn("test-lib");
    Mockito.when(project.getResources()).thenReturn(Collections.singletonList(mainResource));
    Mockito.when(project.getTestResources()).thenReturn(Collections.singletonList(mainResource));

    hashUtil.setProjectTestPhaseDetails(projectStatus, project, additionalFileSets);
    assertEquals(expectedPhaseDetails, projectStatus.getTest().getPhaseDetails());
    assertEquals(expectedPhaseHash, projectStatus.getTest().getPhaseHash());
  }

  @Test
  public void testSetProjectIntegrationTestPhaseDetails() throws IOException {
    String expectedPhaseDetails = "--- class-dependency\n" + "com.test:test-lib:classes-xyz-111\n"
        + "com.test:test-lib:test-classes-abcd-1234\n" + "--- resources\n"
        + TEXT_FILES_SOURCE_HASHES + "--- test-resources\n" + TEXT_FILES_SOURCE_HASHES
        + "--- plugins\n" + INTEGRATION_TEST_PLUGIN_DETAILS;
    String expectedPhaseHash = "c6e605c9af4e07a45bb38568c1dcdf0e";

    testSetProjectIntegrationTestPhaseDetails(Collections.emptyList(), expectedPhaseDetails,
        expectedPhaseHash);
  }

  @Test
  public void testSetProjectIntegrationTestPhaseDetailsWithAdditionalFileSets() throws IOException {
    FileSet additionalFileSet = new FileSet();
    additionalFileSet.setDirectory(FileUtilTest.class.getResource("/").getFile());
    additionalFileSet.addInclude("trigger.txt");

    String expectedPhaseDetails = "--- class-dependency\n" + "com.test:test-lib:classes-xyz-111\n"
        + "com.test:test-lib:test-classes-abcd-1234\n" + "--- resources\n"
        + TEXT_FILES_SOURCE_HASHES + "--- test-resources\n" + TEXT_FILES_SOURCE_HASHES
        + "--- additional-triggers\n" + TRIGGER_TXT + "--- plugins\n"
        + INTEGRATION_TEST_PLUGIN_DETAILS;
    String expectedPhaseHash = "513848b65d3f8a9987303313021d84e6";

    testSetProjectIntegrationTestPhaseDetails(Collections.singletonList(additionalFileSet),
        expectedPhaseDetails, expectedPhaseHash);
  }

  private void testSetProjectIntegrationTestPhaseDetails(List<FileSet> additionalFileSets,
      String expectedPhaseDetails, String expectedPhaseHash) throws IOException {
    ProjectBuildStatus projectStatus = new ProjectBuildStatus();
    projectStatus.setMavenExecutionPlan(mockMavenExecutionPlan());
    projectStatus.getMainCompile().setPhaseHash("xyz-111");
    projectStatus.getTestCompile().setPhaseHash("abcd-1234");

    String resourceRoot =
        Paths.get(FileUtilTest.class.getResource("/test-files").getFile()).toString();

    Resource mainResource = Mockito.mock(Resource.class);
    Mockito.when(mainResource.getDirectory()).thenReturn(resourceRoot);
    Mockito.when(mainResource.getIncludes()).thenReturn(Collections.emptyList());
    Mockito.when(mainResource.getExcludes()).thenReturn(Collections.emptyList());

    MavenProject project = Mockito.mock(MavenProject.class);
    Mockito.when(project.getGroupId()).thenReturn("com.test");
    Mockito.when(project.getArtifactId()).thenReturn("test-lib");
    Mockito.when(project.getResources()).thenReturn(Collections.singletonList(mainResource));
    Mockito.when(project.getTestResources()).thenReturn(Collections.singletonList(mainResource));

    hashUtil.setProjectIntegrationTestPhaseDetails(projectStatus, project, additionalFileSets);
    assertEquals(expectedPhaseDetails, projectStatus.getIntegrationTest().getPhaseDetails());
    assertEquals(expectedPhaseHash, projectStatus.getIntegrationTest().getPhaseHash());
  }

  @Test
  public void testGetPhasePluginExecutionDetails() {

    MavenExecutionPlan mavenExecutionPlan = mockMavenExecutionPlan();

    String phasePluginExecutionDetails = hashUtil.getPhasePluginExecutionDetails(mavenExecutionPlan,
        MojoExecUtil::isCompileRelatedPhase);

    assertEquals(COMPILE_PLUGIN_DETAILS, phasePluginExecutionDetails);
  }

  @Test
  public void testGetSourceFileHashList() throws IOException {
    String testDir = Paths.get(FileUtilTest.class.getResource("/test-files").getFile()).toString();

    String sourceFileHashList = hashUtil.getFileHashList(testDir, TEXT_FILES);

    assertEquals(TEXT_FILES_SOURCE_HASHES, sourceFileHashList);
  }

  @Test
  public void testDependenciesToString() throws IOException {
    BuildCache buildCache = Mockito.mock(BuildCache.class);
    Mockito.when(buildCache.getProjectStatus(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(Optional.empty());

    Set<Artifact> dependencies = mockDependencies();

    assertEquals(DEPENDENCY_LIST, hashUtil.dependenciesToString(buildCache, dependencies));
  }

  @Test
  public void testDependencyToString_partOfBuild() throws IOException {
    Artifact dependency = mockArtifact("com.test", "test-lib", "1.0.0-SNAPSHOT", true);

    ProjectBuildStatus buildStatus = new ProjectBuildStatus();
    buildStatus.getMainCompile().setPhaseHash("1234567890");

    BuildCache buildCache = Mockito.mock(BuildCache.class);
    Mockito.when(buildCache.getProjectStatus("com.test", "test-lib"))
        .thenReturn(Optional.of(buildStatus));

    assertEquals("com.test:test-lib:1234567890",
        hashUtil.dependencyToString(buildCache, dependency));
  }

  @Test
  public void testDependencyToString_releaseVersion() throws IOException {
    Artifact dependency = mockArtifact("com.test", "test-lib", "1.0.0", false);

    BuildCache buildCache = Mockito.mock(BuildCache.class);
    Mockito.when(buildCache.getProjectStatus("com.test", "test-lib")).thenReturn(Optional.empty());

    assertEquals("com.test:test-lib:1.0.0", hashUtil.dependencyToString(buildCache, dependency));
  }

  @Test
  public void testDependencyToString_snapshotVersion() throws IOException {
    Artifact dependency = mockArtifact("com.test", "test-lib", "1.0.0-SNAPSHOT", true);

    File dependencyFile =
        new File(FileUtilTest.class.getResource("/test-files/test.txt").getFile());
    Mockito.when(dependency.getFile()).thenReturn(dependencyFile);

    BuildCache buildCache = Mockito.mock(BuildCache.class);
    Mockito.when(buildCache.getProjectStatus("com.test", "test-lib")).thenReturn(Optional.empty());

    assertEquals("com.test:test-lib:1.0.0-SNAPSHOT:d8e8fca2dc0f896fd7cb4cb0031ba249",
        hashUtil.dependencyToString(buildCache, dependency));
  }

  @Test
  public void testHashString() {
    assertEquals("0f9ca3d1fad3887d0faeb5244844f160", hashUtil.hash("[test, test]"));
  }

  @Test
  public void testHashStringArray() {
    assertEquals("0f9ca3d1fad3887d0faeb5244844f160", hashUtil.hash(new String[] {"test", "test"}));
  }

  @Test
  public void testHashFileContent() throws IOException {
    File testFile = new File(FileUtilTest.class.getResource("/test-files/test.txt").getFile());
    assertEquals("d8e8fca2dc0f896fd7cb4cb0031ba249", hashUtil.hashFileContent(testFile));
  }

  public static Set<Artifact> mockDependencies() {
    Artifact dependency1 = mockArtifact("com.test", "test-lib-b", "1.0.0", false);
    Artifact dependency2 = mockArtifact("com.test", "test-lib-a", "1.0.1", false);
    Artifact dependency3 = mockArtifact("com.foo", "test-lib-x", "2.0.0", false);

    BuildCache buildCache = Mockito.mock(BuildCache.class);
    Mockito.when(buildCache.getProjectStatus(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(Optional.empty());

    Set<Artifact> dependencies = new LinkedHashSet<>();
    dependencies.add(dependency1);
    dependencies.add(dependency2);
    dependencies.add(dependency3);
    return dependencies;
  }

  public static Artifact mockArtifact(String groupId, String artifactId, String version,
      boolean isSnapshot) {
    Artifact dependency = Mockito.mock(Artifact.class);
    Mockito.when(dependency.getGroupId()).thenReturn(groupId);
    Mockito.when(dependency.getArtifactId()).thenReturn(artifactId);
    Mockito.when(dependency.getVersion()).thenReturn(version);
    Mockito.when(dependency.isSnapshot()).thenReturn(isSnapshot);
    return dependency;
  }

  public static MavenExecutionPlan mockMavenExecutionPlan() {
    MojoExecution mojoExec1 =
        mockMojoExecution("compile", "com.test", "test-plugin1", "v1.0.0", "abc\n123");
    MojoExecution mojoExec2 =
        mockMojoExecution("process-classes", "com.test", "test-plugin2", "v2.0.0", "<test/>");
    MojoExecution mojoExec3 =
        mockMojoExecution("test-compile", "com.test", "test-plugin", "v1.0.1", "");
    MojoExecution mojoExec4 = mockMojoExecution("test", "com.test", "test-plugin3", "v3.0.0", null);
    MojoExecution mojoExec5 =
        mockMojoExecution("integration-test", "com.test", "test-plugin4", "v4.0.0", null);

    MavenExecutionPlan mavenExecutionPlan = Mockito.mock(MavenExecutionPlan.class);
    Mockito.when(mavenExecutionPlan.getMojoExecutions())
        .thenReturn(Arrays.asList(mojoExec1, mojoExec2, mojoExec3, mojoExec4, mojoExec5));

    return mavenExecutionPlan;
  }

  public static MojoExecution mockMojoExecution(String lifecyclePhase) {
    return mockMojoExecution(lifecyclePhase, null, null, null, null);
  }

  public static MojoExecution mockMojoExecution(String lifecyclePhase, String groupId,
      String artifactId, String version, String pluginConfiguration) {

    MojoExecution exec = Mockito.mock(MojoExecution.class, Mockito.RETURNS_DEEP_STUBS);
    Mockito.when(exec.getLifecyclePhase()).thenReturn(lifecyclePhase);
    Mockito.when(exec.getGroupId()).thenReturn(groupId);
    Mockito.when(exec.getArtifactId()).thenReturn(artifactId);
    Mockito.when(exec.getVersion()).thenReturn(version);
    Mockito.when(exec.getPlugin().getConfiguration()).thenReturn(pluginConfiguration);

    return exec;
  }
}
