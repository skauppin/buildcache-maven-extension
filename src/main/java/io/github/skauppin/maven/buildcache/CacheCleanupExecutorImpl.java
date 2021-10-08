package io.github.skauppin.maven.buildcache;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.lang3.time.StopWatch;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role = CacheCleanupExecutor.class)
public class CacheCleanupExecutorImpl implements CacheCleanupExecutor {

  private static Pattern buildCacheFilePattern =
      Pattern.compile("[0-9a-f]{32}-(classes|test|test-classes)\\..{2,3}");

  public static boolean isBuildCacheFile(String filename) {
    return buildCacheFilePattern.matcher(filename).matches();
  }

  public static void sortLastModifiedAscending(List<File> files) {
    Collections.sort(files, Comparator.comparingLong(File::lastModified));
  }

  static String byteAmountForOutput(long bytes) {
    if (bytes < 1024) {
      return String.format("%d bytes", bytes);
    }
    if (bytes < 1024 * 1024) {
      return String.format("%.1f Kb", ((float) bytes) / 1024);
    }
    return String.format("%.1f Mb", ((float) bytes) / 1024 / 1024);
  }

  @Requirement
  private Logger logger;

  private FileUtil fileUtil = new FileUtil();

  private Configuration configuration;

  private long cacheEntryExpirationLimit = -1;
  private boolean performTotalCacheSizeCheck = false;
  private long totalCacheSizeLimitBytes = 0;

  @Override
  public void initialize(Configuration configuration) {
    initialize(configuration, System.currentTimeMillis());
  }

  void initialize(Configuration configuration, long nowMillis) {
    this.configuration = configuration;

    if (configuration.hasProjectCacheMaxAge()) {
      this.cacheEntryExpirationLimit = nowMillis - configuration.getProjectCacheMaxAge().toMillis();
    }

    if (configuration.hasTotalCacheMaxSizeMb()) {
      this.performTotalCacheSizeCheck = true;
      this.totalCacheSizeLimitBytes = toBytes(configuration.getTotalCacheMaxSizeMb());
    }
  }

  public boolean hasExpired(File cachedFile) {
    return this.cacheEntryExpirationLimit > 0
        && cachedFile.lastModified() < this.cacheEntryExpirationLimit;
  }

  @Override
  public void fullCacheCleanup() throws IOException {
    StopWatch watch = StopWatch.createStarted();

    Context context = new Context(fileUtil, true);

    cleanupDirectory(context, new File(configuration.getCacheDirectory()));

    if (performTotalCacheSizeCheck && context.totalCacheSizeBytes > totalCacheSizeLimitBytes) {
      context.sortEntries();
      while (context.totalCacheSizeBytes > totalCacheSizeLimitBytes) {
        context.deleteEntry();
      }
    }

    long execTimeSeconds = watch.getTime() / 1000;
    logger.info(String.format("buildcache: full clean deleted %d files (%s) in %d seconds",
        context.deletedFileCount, byteAmountForOutput(context.deletedFileSizeBytes),
        execTimeSeconds));
  }

  @Override
  public void projectCacheCleanup(File projectCacheDirectory) throws IOException {
    Context context = new Context(fileUtil, false);
    cleanupDirectory(context, projectCacheDirectory);
  }

  private void cleanupDirectory(Context context, File directory) throws IOException {

    List<File> directories = new ArrayList<>();
    List<File> cachedFiles = new ArrayList<>();

    int cacheEntries = 0;
    long dirSizeBytes = 0;

    File[] files = fileUtil.listFiles(directory);
    if (files == null) {
      return;
    }

    for (File file : files) {
      if (file.isDirectory()) {
        directories.add(file);

      } else if (isBuildCacheFile(file.getName())) {

        if (hasExpired(file)) {
          context.deleteFile(file);
          continue;

        } else if (countsAsCacheEntry(file)) {
          cacheEntries++;
        }

        dirSizeBytes += file.length();
        cachedFiles.add(file);
      }
    }

    if (configuration.hasProjectCacheMaxSizeMb() || configuration.hasProjectCacheMaxEntries()) {

      long dirSizeLimitBytes = configuration.hasProjectCacheMaxSizeMb()
          ? toBytes(configuration.getProjectCacheMaxSizeMb())
          : Long.MAX_VALUE;

      int cacheEntriesLimit =
          configuration.hasProjectCacheMaxEntries() ? configuration.getProjectCacheMaxEntries()
              : Integer.MAX_VALUE;

      sortLastModifiedAscending(cachedFiles);

      while (dirSizeBytes > dirSizeLimitBytes || cacheEntries > cacheEntriesLimit) {
        File toDelete = cachedFiles.remove(0);
        if (countsAsCacheEntry(toDelete)) {
          cacheEntries--;
        }
        dirSizeBytes -= toDelete.length();
        context.deleteFile(toDelete);
      }
    }

    if (!context.isFullClean) {
      return;
    }

    if (performTotalCacheSizeCheck) {
      context.allCacheEntries.addAll(cachedFiles);
      context.totalCacheSizeBytes += dirSizeBytes;
    }

    for (File subdir : directories) {
      cleanupDirectory(context, subdir);
    }
  }

  private long toBytes(long megaBytes) {
    return megaBytes * 1024 * 1024;
  }

  private boolean countsAsCacheEntry(File file) {
    return file.getName().endsWith(".zip");
  }

  void setLogger(Logger logger) {
    this.logger = logger;
  }

  void setFileUtil(FileUtil fileUtil) {
    this.fileUtil = fileUtil;
  }

  private static class Context {

    private boolean isFullClean;
    private FileUtil fileUtil;

    private List<File> allCacheEntries = new ArrayList<>();
    private long totalCacheSizeBytes = 0;
    private int deletedFileCount = 0;
    private long deletedFileSizeBytes = 0;

    private Context(FileUtil fileUtil, boolean isFullClean) {
      this.fileUtil = fileUtil;
      this.isFullClean = isFullClean;
    }

    private void deleteFile(File file) {
      deletedFileCount++;
      deletedFileSizeBytes += file.length();
      fileUtil.deleteFile(file);
    }

    private void sortEntries() {
      sortLastModifiedAscending(allCacheEntries);
    }

    private void deleteEntry() {
      File toDelete = allCacheEntries.remove(0);
      totalCacheSizeBytes -= toDelete.length();
      deleteFile(toDelete);
    }
  }
}
