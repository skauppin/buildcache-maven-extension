package io.github.skauppin.maven.buildcache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.maven.model.FileSet;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import io.github.skauppin.maven.buildcache.Configuration;
import io.github.skauppin.maven.buildcache.Configuration.ConfigurationException;

public class ConfigurationTest {

  @Test
  public void testReadProjectConfiguration() throws Exception {
    InputStream in = ConfigurationTest.class.getResource("/test-buildcache.xml").openStream();
    Configuration config = new Configuration();
    config.readProjectConfiguration(in);

    MavenProject projectTestLib1 = mockProject("test-lib1");
    MavenProject projectTestLib2 = mockProject("test-lib2");

    List<FileSet> compileFileSetsLib1 = config.getMainCompileTriggers(projectTestLib1);
    assertEquals(2, compileFileSetsLib1.size());

    FileSet compileSet1Lib1 = compileFileSetsLib1.get(0);
    verifyFileSet(compileSet1Lib1, "/base/test-lib1/compile/relative/path",
        Arrays.asList("compile-include-1/*", "compile-include-2/*"),
        Arrays.asList("compile-exclude-3/*"));

    FileSet compileSet2Lib1 = compileFileSetsLib1.get(1);
    verifyFileSet(compileSet2Lib1, "/compile/absolute/path",
        Arrays.asList("compile-include-4/*", "compile-include-5/*"),
        Arrays.asList("compile-exclude-6/*"));

    List<FileSet> testCompileFileSetsLib1 = config.getTestCompileTriggers(projectTestLib1);
    assertEquals(1, testCompileFileSetsLib1.size());

    FileSet testCompileSetLib1 = testCompileFileSetsLib1.get(0);
    verifyFileSet(testCompileSetLib1, "/base/test-lib1/test-compile/relative/path",
        Arrays.asList("test-compile-include-1/*"), Collections.emptyList());

    List<FileSet> testFileSetsLib1 = config.getTestExecutionTriggers(projectTestLib1);
    assertEquals(1, testFileSetsLib1.size());

    FileSet testSetLib1 = testFileSetsLib1.get(0);
    verifyFileSet(testSetLib1, "/base/test-lib1/test/relative/path", Collections.emptyList(),
        Arrays.asList("test-exclude-1/*"));

    List<FileSet> integrationTestFileSetsLib1 =
        config.getIntegrationTestExecutionTriggers(projectTestLib1);
    assertEquals(1, integrationTestFileSetsLib1.size());

    FileSet integrationTestSetLib1 = integrationTestFileSetsLib1.get(0);
    verifyFileSet(integrationTestSetLib1, "/integration-test/absolute/path",
        Arrays.asList("integration-test-include-1/*"),
        Arrays.asList("integration-test-exclude-1/*"));

    List<FileSet> compileFileSetsLib2 = config.getMainCompileTriggers(projectTestLib2);
    assertEquals(2, compileFileSetsLib2.size());

    FileSet compileSet1Lib2 = compileFileSetsLib2.get(0);
    verifyFileSet(compileSet1Lib2, "/base/test-lib2/compile/relative/path/lib2",
        Arrays.asList("compile-include-lib2/*"), Collections.emptyList());

    FileSet compileSet2Lib2 = compileFileSetsLib2.get(1);
    verifyFileSet(compileSet2Lib2, "/compile/absolute/path/lib2", Collections.emptyList(),
        Collections.emptyList());

    assertEquals(0, config.getTestCompileTriggers(projectTestLib2).size());
    assertEquals(0, config.getTestExecutionTriggers(projectTestLib2).size());
  }

  @Test
  public void testReadCacheConfiguration() throws Exception {
    InputStream in =
        ConfigurationTest.class.getResource("/test-buildcache-global.xml").openStream();
    Configuration config = new Configuration();
    config.readCacheConfiguration(in, "/default/cache/dir");

    assertEquals("/root/cache/dir", config.getCacheDirectory());

    assertTrue(config.hasProjectCacheMaxSizeMb());
    assertEquals(7, config.getProjectCacheMaxSizeMb());

    assertTrue(config.hasProjectCacheMaxEntries());
    assertEquals(100, config.getProjectCacheMaxEntries());

    assertTrue(config.hasProjectCacheMaxAge());
    assertEquals(Duration.ofDays(30), config.getProjectCacheMaxAge());

    assertTrue(config.hasTotalCacheMaxSizeMb());
    assertEquals(500, config.getTotalCacheMaxSizeMb());
  }

  @Test
  public void testInvalidRootElement() throws Exception {
    InputStream in = config("<foo></foo>");
    assertThrows(Configuration.ConfigurationException.class,
        () -> new Configuration().readProjectConfiguration(in),
        "Invalid XML configuration: unknown root element <foo>");
  }

  @Test
  public void testProjectWithoutId() throws Exception {
    InputStream in = config("<buildcache><projects><project></project></projects></buildcache>");
    assertThrows(Configuration.ConfigurationException.class,
        () -> new Configuration().readProjectConfiguration(in),
        "Invalid XML configuration: 'id' attribute for <project> has no value");
  }

  @Test
  public void testProjectWithEmptyId() throws Exception {
    InputStream in =
        config("<buildcache><projects><project id=\" \"></project></projects></buildcache>");
    assertThrows(Configuration.ConfigurationException.class,
        () -> new Configuration().readProjectConfiguration(in),
        "Invalid XML configuration: 'id' attribute for <project> has no value");
  }

  @Test
  public void testFileSetWithoutDirectory() throws Exception {
    //@formatter:off
    InputStream in = config("<buildcache><projects><project id=\"test\">"
        + "<compileTriggers>"
        + " <fileSet>"
        + " </fileSet>"
        + "</compileTriggers></project></projects></buildcache>");
    //@formatter:on
    assertThrows(Configuration.ConfigurationException.class,
        () -> new Configuration().readProjectConfiguration(in),
        "Invalid XML configuration: <fileSet> element does not contain <directory>");
  }

  @Test
  public void testFileSetWithEmptyDirectory() throws Exception {
    //@formatter:off
    InputStream in = config("<buildcache><projects><project id=\"test\">"
        + "<compileTriggers>"
        + " <fileSet>"
        + "  <directory> </directory>"
        + " </fileSet>"
        + "</compileTriggers></project></projects></buildcache>");
    //@formatter:on
    assertThrows(Configuration.ConfigurationException.class,
        () -> new Configuration().readProjectConfiguration(in),
        "Invalid XML configuration: <directory> has empty value");
  }

  @Test
  public void testEmptyConfiguration() throws Exception {
    Configuration config = new Configuration();
    config.readCacheConfiguration(config("<buildcache></buildcache>"), "/default/cache/dir");
    config.readProjectConfiguration(config("<buildcache></buildcache>"));

    assertEquals("/default/cache/dir", config.getCacheDirectory());

    assertFalse(config.hasProjectCacheMaxSizeMb());
    assertNull(config.getProjectCacheMaxSizeMb());

    assertFalse(config.hasProjectCacheMaxEntries());
    assertNull(config.getProjectCacheMaxEntries());

    assertFalse(config.hasProjectCacheMaxAge());
    assertNull(config.getProjectCacheMaxAge());

    assertFalse(config.hasTotalCacheMaxSizeMb());
    assertNull(config.getTotalCacheMaxSizeMb());


    MavenProject project = mockProject("test");
    List<FileSet> compileFileSets = config.getMainCompileTriggers(project);
    assertTrue(compileFileSets.isEmpty());
  }

  @Test
  public void testSetCachingDefaults() throws Exception {
    Configuration config = new Configuration();
    config.setCachingDefaults("/default/cache/dir");

    assertEquals("/default/cache/dir", config.getCacheDirectory());
    assertNull(config.getProjectCacheMaxSizeMb());
    assertEquals(20, config.getProjectCacheMaxEntries());
    assertEquals(Duration.ofDays(90), config.getProjectCacheMaxAge());
    assertNull(config.getTotalCacheMaxSizeMb());

    MavenProject project = mockProject("test");
    List<FileSet> compileFileSets = config.getMainCompileTriggers(project);
    assertTrue(compileFileSets.isEmpty());
  }

  @Test
  public void testParseMegabytes() {
    assertEquals(null, Configuration.parseMegabytes(null));
    assertEquals(1, Configuration.parseMegabytes("1"));
    assertEquals(1, Configuration.parseMegabytes("1 "));
    assertEquals(1, Configuration.parseMegabytes("1  "));
    assertEquals(1, Configuration.parseMegabytes("  1  "));
    assertEquals(1, Configuration.parseMegabytes("1m"));
    assertEquals(1, Configuration.parseMegabytes("1M"));
    assertEquals(1, Configuration.parseMegabytes("1 M"));
    assertEquals(1, Configuration.parseMegabytes("1 MB"));
    assertEquals(1, Configuration.parseMegabytes("  1    MB  "));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "x"})
  public void testParseMegabytesErrors(String str) {
    assertThrows(ConfigurationException.class, () -> Configuration.parseMegabytes(str));
  }

  @Test
  public void testParseNumber() {
    assertEquals(null, Configuration.parseNumber(null));
    assertEquals(1, Configuration.parseNumber("1"));
    assertEquals(1, Configuration.parseNumber("1 "));
    assertEquals(1, Configuration.parseNumber("1  "));
    assertEquals(1, Configuration.parseNumber("  1  "));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "x"})
  public void testParseNumberErrors(String str) {
    assertThrows(ConfigurationException.class, () -> Configuration.parseNumber(str));
  }

  @Test
  public void testParseDuration() {
    assertEquals(null, Configuration.parseDuration(null));
    assertEquals(Duration.ofDays(5), Configuration.parseDuration("5"));
    assertEquals(Duration.ofDays(5), Configuration.parseDuration("P5D"));
    assertEquals(Duration.ofDays(5), Configuration.parseDuration("  P5D  "));
    assertEquals(Duration.ofDays(5), Configuration.parseDuration("-P5D"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "x", "5D"})
  public void testParseDurationErrors(String str) {
    assertThrows(ConfigurationException.class, () -> Configuration.parseDuration(str));
  }

  @Test
  private void verifyFileSet(FileSet actual, String directory, List<String> includes,
      List<String> excludes) {
    assertEquals(directory, actual.getDirectory());
    assertEquals(includes, actual.getIncludes());
    assertEquals(excludes, actual.getExcludes());
  }

  private InputStream config(String xml) {
    return new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
  }

  private MavenProject mockProject(String artifactId) {
    MavenProject project = Mockito.mock(MavenProject.class);
    Mockito.when(project.getGroupId()).thenReturn("com.test");
    Mockito.when(project.getArtifactId()).thenReturn(artifactId);
    Mockito.when(project.getBasedir()).thenReturn(new File("/base/" + artifactId));
    return project;
  }
}
