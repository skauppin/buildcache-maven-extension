package io.github.skauppin.maven.buildcache;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role = HashUtil.class)
public class HashUtil {

  @Requirement
  private FileUtil fileUtil;

  @Requirement
  private Logger logger;

  public HashUtil() {}

  public boolean setProjectCompilePhaseDetails(ProjectBuildStatus projectStatus,
      BuildCache buildCache, MavenProject project, List<FileSet> configuredFileSets,
      Map<String, String> properties) throws IOException {

    return setProjectCompilePhaseDetails(projectStatus.getMainCompile(), buildCache,
        projectStatus.getMavenExecutionPlan(), project.getCompileSourceRoots(), configuredFileSets,
        properties, project.getArtifacts(), MojoExecUtil::isCompileRelatedPhase);
  }

  public boolean setProjectTestCompilePhaseDetails(ProjectBuildStatus projectStatus,
      BuildCache buildCache, MavenProject project, List<FileSet> configuredFileSets,
      Map<String, String> properties) throws IOException {

    return setProjectCompilePhaseDetails(projectStatus.getTestCompile(), buildCache,
        projectStatus.getMavenExecutionPlan(), project.getTestCompileSourceRoots(),
        configuredFileSets, properties, project.getArtifacts(),
        MojoExecUtil::isTestCompileRelatedPhase);
  }

  public boolean setProjectTestPhaseDetails(ProjectBuildStatus projectStatus, MavenProject project,
      List<FileSet> configuredFileSets) throws IOException {
    return setProjectTestPhaseDetails(projectStatus.getTest(), projectStatus, project,
        configuredFileSets, MojoExecUtil::isTestRelatedPhase);
  }

  public boolean setProjectIntegrationTestPhaseDetails(ProjectBuildStatus projectStatus,
      MavenProject project, List<FileSet> configuredFileSets) throws IOException {
    return setProjectTestPhaseDetails(projectStatus.getIntegrationTest(), projectStatus, project,
        configuredFileSets, MojoExecUtil::isIntegrationTestRelatedPhase);
  }

  private boolean setProjectTestPhaseDetails(ProjectBuildStatus.Phase currentProjectStatusPhase,
      ProjectBuildStatus projectStatus, MavenProject project, List<FileSet> configuredFileSets,
      Predicate<MojoExecution> filter) throws IOException {

    StringBuilder phaseDetailsBuffer = new StringBuilder();

    String mainClassesDependency = String.format("%s:classes-%s",
        BuildCache.getProjectId(project), projectStatus.getMainCompile().getPhaseHash());

    String testClassesDependency = String.format("%s:test-classes-%s",
        BuildCache.getProjectId(project), projectStatus.getTestCompile().getPhaseHash());

    Pair<List<FileSet>> mergedFileSets1 =
        fileUtil.mergeResourcesToFileSets(project.getResources(), configuredFileSets);

    Pair<List<FileSet>> mergedFileSets2 =
        fileUtil.mergeResourcesToFileSets(project.getTestResources(), mergedFileSets1.getB());

    List<FileSet> mainResources = mergedFileSets1.getA();
    List<FileSet> testResources = mergedFileSets2.getA();
    List<FileSet> additionalFileSets = mergedFileSets2.getB();

    phaseDetailsBuffer.append("--- class-dependency").append("\n");
    phaseDetailsBuffer.append(mainClassesDependency).append("\n");
    phaseDetailsBuffer.append(testClassesDependency).append("\n");

    phaseDetailsBuffer.append("--- resources").append("\n");
    scanAndAppendFileSetHashes(mainResources, phaseDetailsBuffer);

    phaseDetailsBuffer.append("--- test-resources").append("\n");
    scanAndAppendFileSetHashes(testResources, phaseDetailsBuffer);

    if (!additionalFileSets.isEmpty()) {
      phaseDetailsBuffer.append("--- additional-triggers").append("\n");
      scanAndAppendFileSetHashes(additionalFileSets, phaseDetailsBuffer, true);
    }

    phaseDetailsBuffer.append("--- plugins").append("\n");
    String phasePluginExecDetails =
        getPhasePluginExecutionDetails(projectStatus.getMavenExecutionPlan(), filter);
    phaseDetailsBuffer.append(phasePluginExecDetails);

    String phaseDetails = phaseDetailsBuffer.toString();
    currentProjectStatusPhase.setPhaseDetails(phaseDetails);
    currentProjectStatusPhase.setPhaseHash(hash(phaseDetails));

    return true;
  }

  private boolean setProjectCompilePhaseDetails(ProjectBuildStatus.Phase currentProjectStatusPhase,
      BuildCache buildCache, MavenExecutionPlan mavenExecutionPlan, List<String> sourceRoots,
      List<FileSet> configuredFileSets, Map<String, String> properties, Set<Artifact> dependencies,
      Predicate<MojoExecution> filter) throws IOException {

    StringBuilder phaseDetailsBuffer = new StringBuilder();

    Pair<List<FileSet>> mergedFileSets =
        fileUtil.mergeSourceRootsToFileSets(sourceRoots, configuredFileSets);

    List<FileSet> sourceRootFileSets = mergedFileSets.getA();
    phaseDetailsBuffer.append("--- sources").append("\n");
    scanAndAppendFileSetHashes(sourceRootFileSets, phaseDetailsBuffer);

    List<FileSet> additionalFileSets = mergedFileSets.getB();
    if (!additionalFileSets.isEmpty()) {
      phaseDetailsBuffer.append("--- additional-triggers").append("\n");
      scanAndAppendFileSetHashes(additionalFileSets, phaseDetailsBuffer, true);
    }

    phaseDetailsBuffer.append("--- dependencies").append("\n");
    String dependencyList = dependenciesToString(buildCache, dependencies);
    phaseDetailsBuffer.append(dependencyList);

    if (!properties.isEmpty()) {
      phaseDetailsBuffer.append("--- properties").append("\n");
      properties.entrySet().forEach(
          e -> phaseDetailsBuffer.append(e.getKey()).append(":").append(e.getValue()).append("\n"));
    }

    phaseDetailsBuffer.append("--- plugins").append("\n");
    String phasePluginExecDetails = getPhasePluginExecutionDetails(mavenExecutionPlan, filter);
    phaseDetailsBuffer.append(phasePluginExecDetails);

    String phaseDetails = phaseDetailsBuffer.toString();
    currentProjectStatusPhase.setPhaseDetails(phaseDetails);
    currentProjectStatusPhase.setPhaseHash(hash(phaseDetails));

    return true;
  }

  String getPhasePluginExecutionDetails(MavenExecutionPlan mavenExecutionPlan,
      Predicate<MojoExecution> filter) {

    List<MojoExecution> phaseExecutions =
        mavenExecutionPlan.getMojoExecutions().stream().filter(filter).collect(Collectors.toList());

    StringBuilder buffer = new StringBuilder();
    for (MojoExecution mojo : phaseExecutions) {
      buffer.append(mojo.getLifecyclePhase()).append(" ").append(mojo.getGroupId()).append(":")
          .append(mojo.getArtifactId()).append(":").append(mojo.getVersion()).append("\n");
      Object pluginConfig = mojo.getPlugin().getConfiguration();
      if (pluginConfig != null) {
        buffer.append(pluginConfig).append("\n");
      }
    }
    return buffer.toString();
  }

  private void scanAndAppendFileSetHashes(List<FileSet> fileSets, StringBuilder buffer)
      throws IOException {
    scanAndAppendFileSetHashes(fileSets, buffer, false);
  }

  private void scanAndAppendFileSetHashes(List<FileSet> fileSets, StringBuilder buffer,
      boolean logWarningWhenNoFilesFound) throws IOException {

    for (FileSet set : fileSets) {
      String[] filenames = fileUtil.scanFiles(set);
      if (logWarningWhenNoFilesFound && filenames.length == 0) {
        logger.warn(
            String.format("buildcache: fileSet configured in buildcache.xml results empty: %s",
                set.getDirectory()));
      }
      String sourceHashList = getFileHashList(set.getDirectory(), filenames);
      buffer.append(sourceHashList);
    }
  }

  String getFileHashList(String basedir, String[] filenames) throws IOException {
    StringBuilder buffer = new StringBuilder();
    for (String filename : filenames) {
      File file = Paths.get(basedir, filename).toFile();
      buffer.append(filename).append(":").append(hashFileContent(file)).append("\n");
    }
    return buffer.toString();
  }

  String dependenciesToString(BuildCache buildCache, Set<Artifact> projectDependencies)
      throws IOException {
    List<String> dependencyDetails = new ArrayList<>(projectDependencies.size());
    for (Artifact dependency : projectDependencies) {
      dependencyDetails.add(dependencyToString(buildCache, dependency));
    }
    Collections.sort(dependencyDetails);
    return String.join("\n", dependencyDetails) + "\n";
  }

  String dependencyToString(BuildCache buildCache, Artifact dependency) throws IOException {

    String groupId = dependency.getGroupId();
    String artifactId = dependency.getArtifactId();
    Optional<String> buildCacheHash = buildCache.getProjectStatus(groupId, artifactId)
        .map(s -> s.getMainCompile().getPhaseHash());

    if (buildCacheHash.isPresent()) {
      return String.format("%s:%s:%s", groupId, artifactId, buildCacheHash.get());
    }

    String version = dependency.getVersion();
    if (dependency.isSnapshot()) {
      version += ":" + hashFileContent(dependency.getFile());
    }
    return String.format("%s:%s:%s", dependency.getGroupId(), dependency.getArtifactId(), version);
  }

  public String hash(String str) {
    return DigestUtils.md5Hex(str);
  }

  public String hash(String[] strings) {
    return DigestUtils.md5Hex(Arrays.toString(strings));
  }

  public String hashFileContent(File f) throws IOException {
    try (FileInputStream in = new FileInputStream(f)) {
      return DigestUtils.md5Hex(in);
    }
  }

  void setLogger(Logger logger) {
    this.logger = logger;
  }

  void setFileUtil(FileUtil fileUtil) {
    this.fileUtil = fileUtil;
  }
}
