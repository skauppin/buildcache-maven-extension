package io.github.skauppin.maven.buildcache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.model.fileset.FileSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.google.common.collect.ImmutableList;

public class FileUtilTest {

  private static List<String> CLASS_FILES;

  //@formatter:off
  public static List<String> TEXT_FILES
    = Arrays.asList(
        "test-buildcache-global.xml",
        "test-buildcache.xml",
        "test-files/test.txt",
        "test-files/test1/test1.txt",
        "test-files/test1/test2/test2.txt",
        "trigger.txt");
  //@formatter:on

  private static List<String> ALL_FILES = new ArrayList<>();

  private FileUtil fileUtil = new FileUtil();

  @BeforeAll
  public static void init() throws IOException {
    Path targetDir = Paths.get(FileUtilTest.class.getResource("/").getFile());
    Path classDir = targetDir.resolve(Paths.get("io", "github", "skauppin", "maven", "buildcache"));
    List<String> files = Files.list(classDir).map(p -> targetDir.relativize(p).toString())
        .collect(Collectors.toList());
    Collections.sort(files);
    CLASS_FILES = ImmutableList.copyOf(files);

    ALL_FILES.addAll(CLASS_FILES);
    ALL_FILES.addAll(TEXT_FILES);
  }

  @Test
  public void testMergeSourceRootsToFileSetsWithMatchingDirectory() {
    List<String> sourceRoots = Collections.singletonList("/root/source");

    FileSet set = new FileSet();
    set.setDirectory("/root/source");
    set.addInclude("include");
    set.addExclude("exclude");

    Pair<List<FileSet>> mergedFilesets =
        fileUtil.mergeSourceRootsToFileSets(sourceRoots, Collections.singletonList(set));
    assertEquals(0, mergedFilesets.getB().size());

    List<FileSet> sourceRootFileSets = mergedFilesets.getA();
    assertEquals(1, sourceRootFileSets.size());

    FileSet mergedSet = sourceRootFileSets.get(0);
    assertEquals("/root/source", mergedSet.getDirectory());
    assertEquals(Arrays.asList("include"), mergedSet.getIncludes());
    assertEquals(Arrays.asList("exclude"), mergedSet.getExcludes());
  }

  @Test
  public void testMergeSourceRootsToFileSetsWithDifferentDirectories() {
    List<String> sourceRoots = Collections.singletonList("/root/source");

    FileSet set1 = new FileSet();
    set1.setDirectory("/root/source2");
    set1.addInclude("include-1");
    set1.addExclude("exclude-1");

    FileSet set2 = new FileSet();
    set2.setDirectory("/root/source2");
    set2.addInclude("include-2");
    set2.addExclude("exclude-2");

    Pair<List<FileSet>> mergedFilesets =
        fileUtil.mergeSourceRootsToFileSets(sourceRoots, Arrays.asList(set1, set2));

    List<FileSet> sourceRootFileSets = mergedFilesets.getA();
    assertEquals(1, sourceRootFileSets.size());
    List<FileSet> additionalFileSets = mergedFilesets.getB();
    assertEquals(1, additionalFileSets.size());

    FileSet mergedSet1 = sourceRootFileSets.get(0);
    assertEquals("/root/source", mergedSet1.getDirectory());
    assertEquals(Collections.emptyList(), mergedSet1.getIncludes());
    assertEquals(0, mergedSet1.getExcludes().size());

    FileSet mergedSet2 = additionalFileSets.get(0);
    assertEquals("/root/source2", mergedSet2.getDirectory());
    assertEquals(Arrays.asList("include-1", "include-2"), mergedSet2.getIncludes());
    assertEquals(Arrays.asList("exclude-1", "exclude-2"), mergedSet2.getExcludes());
  }

  @Test
  public void testCopyFileSet() {
    FileSet set = new FileSet();
    set.setDirectory("/root");
    set.addInclude("include");
    set.addExclude("exclude");
    set.setFollowSymlinks(true);
    set.setUseDefaultExcludes(false);

    FileSet copy = fileUtil.copyFileSet(set);
    assertFalse(set == copy);
    assertFalse(set.getIncludes() == copy.getIncludes());
    assertFalse(set.getExcludes() == copy.getExcludes());
    assertEquals(set.getDirectory(), copy.getDirectory());
    assertEquals(set.getIncludes(), copy.getIncludes());
    assertEquals(set.getExcludes(), copy.getExcludes());
    assertTrue(copy.isFollowSymlinks());
    assertFalse(copy.isUseDefaultExcludes());
  }

  @Test
  public void testScanFilesNonExisting() {
    String targetDir = FileUtilTest.class.getResource("/").getFile() + "non-existing";
    FileSet set = new FileSet();
    set.setDirectory(targetDir);
    verifyScanFiles(set, Collections.emptyList());
  }

  @Test
  public void testScanFiles() {
    String targetDir = FileUtilTest.class.getResource("/").getFile();
    FileSet set = new FileSet();
    set.setDirectory(targetDir);
    verifyScanFiles(set, ALL_FILES);
  }

  @Test
  public void testScanFilesInclude() {
    String targetDir = FileUtilTest.class.getResource("/").getFile();
    FileSet set = new FileSet();
    set.setDirectory(targetDir);
    set.addInclude("**/*.class");
    verifyScanFiles(set, CLASS_FILES);
  }

  @Test
  public void testScanFilesExclude() {
    String targetDir = FileUtilTest.class.getResource("/").getFile();
    FileSet set = new FileSet();
    set.setDirectory(targetDir);
    set.addExclude("**/*.class");
    verifyScanFiles(set, TEXT_FILES);
  }

  @Test
  public void testScanFilesFromDirectory() {
    Path targetDir = Paths.get(FileUtilTest.class.getResource("/").getFile());
    String[] filenames = fileUtil.scanFilesFromDirectory(targetDir);

    assertEquals(ALL_FILES.size(), filenames.length);
    for (int i = 0; i < ALL_FILES.size(); i++) {
      assertEquals(ALL_FILES.get(i), filenames[i]);
    }
  }

  @Test
  public void testScanClassFilesFromDirectory() {
    Path targetDir = Paths.get(FileUtilTest.class.getResource("/").getFile());
    String[] files = fileUtil.scanClassFilesFromDirectory(targetDir);
    assertEquals(CLASS_FILES, Arrays.asList(files));
  }

  @Test
  public void testScanFilesFromDirectoryWithInclude() {
    Path targetDir = Paths.get(FileUtilTest.class.getResource("/").getFile());
    String[] files = fileUtil.scanFilesFromDirectory(targetDir, "**/*.class");
    assertEquals(CLASS_FILES, Arrays.asList(files));
  }

  @Test
  public void testCreateZipFileAndUnzip() {
    Path zipFile = null;
    Path unzipDir = null;
    try {
      Path archiveBaseDir = Paths.get(FileUtilTest.class.getResource("/").getFile());
      zipFile = Files.createTempFile("buildcache-maven-extension-", ".zip");
      fileUtil.createZipFile(zipFile, archiveBaseDir, TEXT_FILES.toArray(new String[0]));

      assertTrue(zipFile.toFile().exists());

      unzipDir = Files.createTempDirectory("buildcache-maven-extension-");
      fileUtil.unzip(zipFile, unzipDir);

      String[] unzippedFiles = fileUtil.scanFilesFromDirectory(unzipDir);
      assertEquals(TEXT_FILES, Arrays.asList(unzippedFiles));

    } catch (Exception e) {
      fail(e);

    } finally {
      try {
        if (zipFile != null && zipFile.toFile().exists()) {
          zipFile.toFile().delete();
        }
      } catch (Exception e) {
      }
      if (unzipDir != null) {
        FileUtils.deleteQuietly(unzipDir.toFile());
      }
    }
  }

  private void verifyScanFiles(FileSet set, List<String> expectedFiles) {
    List<String> files = Arrays.asList(fileUtil.scanFiles(set));
    assertEquals(expectedFiles, files);
  }
}
