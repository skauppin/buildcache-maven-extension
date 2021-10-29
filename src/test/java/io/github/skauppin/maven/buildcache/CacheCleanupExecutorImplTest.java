package io.github.skauppin.maven.buildcache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.codehaus.plexus.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class CacheCleanupExecutorImplTest {

  private Configuration configuration;
  private FileUtil fileUtil;
  private Logger logger;

  private CacheCleanupExecutorImpl executor;

  private static final long NOW = toEpochMillis("2021-09-14T10:00:00Z");

  @BeforeEach
  public void init() {
    this.configuration = Mockito.mock(Configuration.class);
    Mockito.when(this.configuration.getCacheDirectory()).thenReturn("/user/home/.m2/buildcache");
    Mockito.when(this.configuration.getProjectCacheMaxAge()).thenReturn(null);
    Mockito.when(this.configuration.getProjectCacheMaxEntries()).thenReturn(null);
    Mockito.when(this.configuration.getProjectCacheMaxSizeMb()).thenReturn(null);
    Mockito.when(this.configuration.getTotalCacheMaxSizeMb()).thenReturn(null);

    this.fileUtil = Mockito.mock(FileUtil.class);
    this.logger = Mockito.mock(Logger.class);

    executor = new CacheCleanupExecutorImpl();
    executor.setFileUtil(fileUtil);
    executor.setLogger(logger);
  }

  @Test
  public void testIsBuildCacheFile() {
    assertTrue(isBuildCacheFile("fada9a5d114c919f8406af300ac16c95-classes.zip"));
    assertTrue(isBuildCacheFile("fada9a5d114c919f8406af300ac16c95-classes.txt"));
    assertTrue(isBuildCacheFile("4196bea62dd477ad658a6c62553770d9-test-classes.zip"));
    assertTrue(isBuildCacheFile("4196bea62dd477ad658a6c62553770d9-test-classes.txt"));
    assertTrue(isBuildCacheFile("37c1e2c0853b2596629532054426fc8c-test.txt"));
    assertTrue(isBuildCacheFile("37c1e2c0853b2596629532054426fc8c-test.ok"));

    assertFalse(isBuildCacheFile("fada.zip"));
    assertFalse(isBuildCacheFile("fada9a5d114c919f8406af300ac16c95-.zip"));
  }

  private boolean isBuildCacheFile(String filename) {
    return CacheCleanupExecutorImpl.isBuildCacheFile(filename);
  }

  @Test
  public void testSortLastModifiedAscending() {
    File f1 = Mockito.mock(File.class);
    Mockito.when(f1.lastModified()).thenReturn(10L);

    File f2 = Mockito.mock(File.class);
    Mockito.when(f2.lastModified()).thenReturn(9L);

    File f3 = Mockito.mock(File.class);
    Mockito.when(f3.lastModified()).thenReturn(8L);

    List<File> files = Arrays.asList(f1, f2, f3);
    CacheCleanupExecutorImpl.sortLastModifiedAscending(files);

    assertEquals(Arrays.asList(f3, f2, f1), files);
  }

  @Test
  public void testByteAmountForOutput() {
    assertEquals("0 bytes", CacheCleanupExecutorImpl.byteAmountForOutput(0));
    assertEquals("1023 bytes", CacheCleanupExecutorImpl.byteAmountForOutput(1023));
    assertEquals("1.0 Kb", CacheCleanupExecutorImpl.byteAmountForOutput(1024));
    assertEquals("10.0 Kb", CacheCleanupExecutorImpl.byteAmountForOutput(10 * 1024));
    assertEquals("512.1 Kb", CacheCleanupExecutorImpl.byteAmountForOutput(1024 * 512 + 100));
    assertEquals("1.0 Mb", CacheCleanupExecutorImpl.byteAmountForOutput(1024 * 1024));
    assertEquals("1.4 Mb", CacheCleanupExecutorImpl.byteAmountForOutput(1424 * 1024));
    assertEquals("1224.0 Mb", CacheCleanupExecutorImpl.byteAmountForOutput(1224 * 1024 * 1024));
  }

  @Test
  public void testHasExpired() {
    Mockito.when(configuration.hasProjectCacheMaxAge()).thenReturn(true);
    Mockito.when(configuration.getProjectCacheMaxAge()).thenReturn(Duration.ofDays(10));

    executor.initialize(configuration, NOW);

    File cachedFile = Mockito.mock(File.class);

    Mockito.when(cachedFile.lastModified()) //
        .thenReturn(toEpochMillis("2021-09-14T10:00:00Z"))
        .thenReturn(toEpochMillis("2021-09-04T10:00:00Z"))
        .thenReturn(toEpochMillis("2021-09-04T09:59:59Z"))
        .thenReturn(toEpochMillis("2021-09-01T00:00:00Z"));

    assertFalse(executor.hasExpired(cachedFile));
    assertFalse(executor.hasExpired(cachedFile));
    assertTrue(executor.hasExpired(cachedFile));
    assertTrue(executor.hasExpired(cachedFile));
  }

  private static long toEpochMillis(String instant) {
    return Instant.parse(instant).getEpochSecond() * 1000;
  }

  @Test
  public void testFullCleanupWithNoLimits() throws IOException {

    File[] testFiles1 = mockTestFiles();
    File[] testFiles2 = mockTestFiles();
    mockCacheDirFiles(testFiles1, testFiles2);

    executor.initialize(configuration, NOW);
    executor.fullCacheCleanup();

    Mockito.verify(fileUtil, Mockito.times(3)).listFiles(Mockito.any());
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testFullCleanupWithMaxAge() throws IOException {
    Mockito.when(configuration.hasProjectCacheMaxAge()).thenReturn(true);
    Mockito.when(configuration.getProjectCacheMaxAge()).thenReturn(Duration.ofDays(10));

    File[] testFiles1 = mockTestFiles();
    File[] testFiles2 = mockTestFiles();
    mockCacheDirFiles(testFiles1, testFiles2);

    executor.initialize(configuration, NOW);
    executor.fullCacheCleanup();

    Mockito.verify(fileUtil).deleteFile(testFiles1[6]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[7]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[6]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[7]);
    Mockito.verify(fileUtil, Mockito.times(3)).listFiles(Mockito.any());
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testFullCleanupWithMaxSize() throws IOException {
    Mockito.when(configuration.hasProjectCacheMaxSizeMb()).thenReturn(true);
    Mockito.when(configuration.getProjectCacheMaxSizeMb()).thenReturn(1);

    File[] testFiles1 = mockTestFiles();
    File[] testFiles2 = mockTestFiles();
    mockCacheDirFiles(testFiles1, testFiles2);

    executor.initialize(configuration, NOW);
    executor.fullCacheCleanup();

    Mockito.verify(fileUtil).deleteFile(testFiles1[4]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[6]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[7]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[4]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[6]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[7]);
    Mockito.verify(fileUtil, Mockito.times(3)).listFiles(Mockito.any());
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testFullCleanupWithMaxEntries() throws IOException {
    Mockito.when(configuration.hasProjectCacheMaxEntries()).thenReturn(true);
    Mockito.when(configuration.getProjectCacheMaxEntries()).thenReturn(2);

    File[] testFiles1 = mockTestFiles();
    File[] testFiles2 = mockTestFiles();
    mockCacheDirFiles(testFiles1, testFiles2);

    executor.initialize(configuration, NOW);
    executor.fullCacheCleanup();

    Mockito.verify(fileUtil).deleteFile(testFiles1[4]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[6]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[7]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[4]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[6]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[7]);
    Mockito.verify(fileUtil, Mockito.times(3)).listFiles(Mockito.any());
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testFullCleanupWithProjectLimits() throws IOException {
    Mockito.when(configuration.hasProjectCacheMaxAge()).thenReturn(true);
    Mockito.when(configuration.getProjectCacheMaxAge()).thenReturn(Duration.ofDays(10));
    Mockito.when(configuration.hasProjectCacheMaxSizeMb()).thenReturn(true);
    Mockito.when(configuration.getProjectCacheMaxSizeMb()).thenReturn(1);
    Mockito.when(configuration.hasProjectCacheMaxEntries()).thenReturn(true);
    Mockito.when(configuration.getProjectCacheMaxEntries()).thenReturn(1);

    File[] testFiles1 = mockTestFiles();
    File[] testFiles2 = mockTestFiles();
    mockCacheDirFiles(testFiles1, testFiles2);

    executor.initialize(configuration, NOW);
    executor.fullCacheCleanup();

    Mockito.verify(fileUtil).deleteFile(testFiles1[2]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[4]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[5]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[6]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[7]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[2]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[4]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[5]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[6]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[7]);
    Mockito.verify(fileUtil, Mockito.times(3)).listFiles(Mockito.any());
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testFullCleanupWithGlobalCacheSizeLimit() throws IOException {
    Mockito.when(configuration.hasTotalCacheMaxSizeMb()).thenReturn(true);
    Mockito.when(configuration.getTotalCacheMaxSizeMb()).thenReturn(1);

    File[] testFiles1 = mockTestFiles();
    File[] testFiles2 = mockTestFiles();
    mockCacheDirFiles(testFiles1, testFiles2);

    executor.initialize(configuration, NOW);
    executor.fullCacheCleanup();

    Mockito.verify(fileUtil).deleteFile(testFiles1[2]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[4]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[5]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[6]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[7]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[2]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[4]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[5]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[6]);
    Mockito.verify(fileUtil).deleteFile(testFiles2[7]);
    Mockito.verify(fileUtil, Mockito.times(3)).listFiles(Mockito.any());
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  @Test
  public void testProjectCleanup() throws IOException {
    Mockito.when(configuration.hasProjectCacheMaxAge()).thenReturn(true);
    Mockito.when(configuration.getProjectCacheMaxAge()).thenReturn(Duration.ofDays(10));

    File[] testFiles1 = mockTestFiles();

    Mockito.when(fileUtil.listFiles(Mockito.any())).thenAnswer(invocation -> {
      String dir = invocation.getArgument(0, File.class).getName();
      if ("project-dir".equals(dir)) {
        return ArrayUtils.addAll(testFiles1, mockDir("subdir"));
      }
      return null;
    });

    File projectDir = new File("project-dir");

    executor.initialize(configuration, NOW);
    executor.projectCacheCleanup(projectDir);

    Mockito.verify(fileUtil).deleteFile(testFiles1[6]);
    Mockito.verify(fileUtil).deleteFile(testFiles1[7]);
    Mockito.verify(fileUtil, Mockito.times(1)).listFiles(Mockito.eq(projectDir));
    Mockito.verifyNoMoreInteractions(fileUtil);
  }

  private void mockCacheDirFiles(File[] files1, File[] files2) {
    Mockito.when(fileUtil.listFiles(Mockito.any())).thenAnswer(invocation -> {
      String dir = invocation.getArgument(0, File.class).getName();
      if ("buildcache".equals(dir)) {
        return new File[] {mockDir("subdir1"), mockDir("subdir2")};

      } else if ("subdir1".equals(dir)) {
        return files1;

      } else if ("subdir2".equals(dir)) {
        return files2;
      }

      return null;
    });
  }

  private File[] mockTestFiles() {
    File f0 =
        mockFile("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-classes.zip", "2021-09-14T10:00:00Z", kb(500));
    File f1 =
        mockFile("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-classes.txt", "2021-09-14T10:00:01Z", kb(4));
    File f2 =
        mockFile("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb-classes.zip", "2021-09-13T10:00:00Z", kb(500));
    File f3 =
        mockFile("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb-classes.txt", "2021-09-13T10:00:01Z", kb(4));
    File f4 =
        mockFile("cccccccccccccccccccccccccccccccc-classes.zip", "2021-09-13T09:00:00Z", kb(500));
    File f5 =
        mockFile("cccccccccccccccccccccccccccccccc-classes.txt", "2021-09-13T09:00:01Z", kb(4));
    File f6 =
        mockFile("dddddddddddddddddddddddddddddddd-classes.zip", "2021-09-04T09:00:00Z", kb(500));
    File f7 =
        mockFile("dddddddddddddddddddddddddddddddd-classes.txt", "2021-09-04T09:00:01Z", kb(4));
    File f8 = mockFile("not-a-buildcache-file.zip", "2000-01-01T00:00:00Z", 1);
    return new File[] {f0, f1, f2, f3, f4, f5, f6, f7, f8};
  }

  private int kb(int n) {
    return n * 1024;
  }

  private File mockDir(String name) {
    File dir = Mockito.mock(File.class);
    Mockito.when(dir.isDirectory()).thenReturn(true);
    Mockito.when(dir.getName()).thenReturn(name);
    return dir;
  }

  private File mockFile(String name, String lastModified, long length) {
    File file = Mockito.mock(File.class);
    Mockito.when(file.getName()).thenReturn(name);
    Mockito.when(file.lastModified()).thenReturn(toEpochMillis(lastModified));
    Mockito.when(file.length()).thenReturn(length);
    return file;
  }
}
