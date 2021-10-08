package io.github.skauppin.maven.buildcache;

import java.io.File;
import java.io.IOException;

public interface CacheCleanupExecutor {

  void initialize(Configuration configuration);

  void fullCacheCleanup() throws IOException;

  void projectCacheCleanup(File projectCacheDirectory) throws IOException;
}
