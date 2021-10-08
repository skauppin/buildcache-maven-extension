package io.github.skauppin.maven.buildcache;

import java.util.Optional;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;

public interface BuildCache {

  boolean isInitializationError();

  String getInitializationErrorMessage();

  void initializeSession(MavenSession session);

  boolean isInitialized();

  boolean isBuildCacheDebug();

  boolean isBuildCacheProfile();

  void cleanProject(MavenSession session);

  ProjectBuildStatus getProjectStatus(MavenSession session);

  ProjectBuildStatus getProjectStatus(MavenSession session, MojoExecution mojoExecution);

  Optional<ProjectBuildStatus> getProjectStatus(String groupId, String artifactId);

  boolean useCachedMainClasses(MavenSession session);

  boolean useCachedTestClasses(MavenSession session);

  boolean isTestExecutionCacheHit(MavenSession session);

  boolean isIntegrationTestExecutionCacheHit(MavenSession session);

  void cacheMainClasses(MavenSession session);

  void cacheTestClasses(MavenSession session);

  void cacheTestExecution(MavenSession session);

  void cacheIntegrationTestExecution(MavenSession session);

  void writeMainCompileDetails(MavenSession session);

  void writeTestCompileDetails(MavenSession session);

  void writeTestExecutionDetails(MavenSession session);

  void writeIntegrationTestExecutionDetails(MavenSession session);
}
