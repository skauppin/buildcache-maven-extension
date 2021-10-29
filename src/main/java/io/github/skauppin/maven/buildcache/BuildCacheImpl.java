package io.github.skauppin.maven.buildcache;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;

@Component(role = BuildCache.class)
public class BuildCacheImpl implements BuildCache, Initializable {

  public static String getProjectId(MavenProject project) {
    return String.format("%s:%s", project.getGroupId(), project.getArtifactId());
  }

  public static final String MAVEN_HOME = "maven.home";
  public static final String USER_HOME = "user.home";

  public static final String MAVEN_MAIN_SKIP = "maven.main.skip";
  public static final String MAVEN_TEST_SKIP = "maven.test.skip";
  public static final String SKIP_TESTS = "skipTests";
  public static final String SKIP_IT_TESTS = "skipITs";
  public static final String TEST_SUBSET = "test";
  public static final String IT_TEST_SUBSET = "it.test";

  public static final String MAVEN_COMPILER_SOURCE = "maven.compiler.source";
  public static final String MAVEN_COMPILER_TARGET = "maven.compiler.target";

  public static final String BUILD_CACHE_DISABLE = "buildcache.disable";

  public static final String BUILD_CACHE_DEBUG = "buildcache.debug";
  public static final String BUILD_CACHE_PROFILE = "buildcache.profile";
  public static final String BUILD_CACHE_IGNORE = "buildcache.ignore";
  public static final String BUILD_CACHE_FULL_CLEAN = "buildcache.fullclean";

  private static final String CONFIG_FILENAME = "buildcache.xml";
  private static final String MVN_DIR = ".mvn";
  private static final String USER_M2_DIR = ".m2";
  private static final String MAVEN_CONF_DIR = "conf";
  private static final String USER_M2_CACHE_DIR = "buildcache";
  private static final String CLASSES_DIR = "classes";
  private static final String TEST_CLASSES_DIR = "test-classes";

  @Requirement
  private Logger logger;

  @Requirement
  private LifecycleExecutor lifecycleExecutor;

  @Requirement
  private CacheCleanupExecutor cacheCleanupExecutor;

  private HashUtil hashUtil = new HashUtil();

  private boolean initialized = false;
  private boolean error = false;
  private String errorMessage = null;

  private boolean buildCacheDisabled = false;
  private boolean buildCacheDebug = false;
  private boolean buildCacheIgnore = false;
  private boolean buildCacheProfile = false;

  private Map<String, String> compilePhaseProperties = new TreeMap<>();

  private Configuration configuration = new Configuration();

  private Map<String, ProjectBuildStatus> projectStatusMap =
      Collections.synchronizedMap(new HashMap<>());

  private Function<MavenSession, PluginParameterExpressionEvaluator> expressionEvaluatorProvider =
      s -> new PluginParameterExpressionEvaluator(s, new MojoExecution(null));

  private FileUtil fileUtil = new FileUtil();

  @Override
  public boolean isInitializationError() {
    return error;
  }

  @Override
  public String getInitializationErrorMessage() {
    return errorMessage;
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }

  @Override
  public void initialize() {
    hashUtil.setLogger(logger);
  }

  @Override
  public void initializeSession(MavenSession session) {

    PluginParameterExpressionEvaluator expressionEvaluator =
        expressionEvaluatorProvider.apply(session);

    String userHome = null;
    String mavenHome = null;
    boolean fullCacheClean = false;

    try {
      try {
        String mavenCompilerSource = evaluate(expressionEvaluator, MAVEN_COMPILER_SOURCE);
        if (mavenCompilerSource != null) {
          compilePhaseProperties.put(MAVEN_COMPILER_SOURCE, mavenCompilerSource);
        }
        String mavenCompilerTarget = evaluate(expressionEvaluator, MAVEN_COMPILER_TARGET);
        if (mavenCompilerTarget != null) {
          compilePhaseProperties.put(MAVEN_COMPILER_TARGET, mavenCompilerTarget);
        }

        userHome = evaluate(expressionEvaluator, USER_HOME);
        mavenHome = evaluate(expressionEvaluator, MAVEN_HOME);
        fullCacheClean = checkProperty(expressionEvaluator, BUILD_CACHE_FULL_CLEAN);

        buildCacheDisabled = checkProperty(expressionEvaluator, BUILD_CACHE_DISABLE);
        buildCacheDebug = checkProperty(expressionEvaluator, BUILD_CACHE_DEBUG);
        buildCacheIgnore = checkProperty(expressionEvaluator, BUILD_CACHE_IGNORE);
        buildCacheProfile = checkProperty(expressionEvaluator, BUILD_CACHE_PROFILE);

      } catch (Exception e) {
        throw new InitializationError(
            "Unable to resolve user.home, maven.home, or other needed parameters", e);
      }

      File projectCacheConfigFile =
          Paths.get(session.getExecutionRootDirectory(), MVN_DIR, CONFIG_FILENAME).toFile();

      File cacheConfigurationFile = Paths.get(userHome, USER_M2_DIR, CONFIG_FILENAME).toFile();
      if (!cacheConfigurationFile.exists()) {
        cacheConfigurationFile = Paths.get(mavenHome, MAVEN_CONF_DIR, CONFIG_FILENAME).toFile();
      }

      Path defaultCacheDir = Paths.get(userHome, USER_M2_DIR, USER_M2_CACHE_DIR);

      File config = projectCacheConfigFile;
      try {
        if (projectCacheConfigFile.exists()) {
          configuration.readProjectConfiguration(new FileInputStream(projectCacheConfigFile));
        }
        config = cacheConfigurationFile;
        if (cacheConfigurationFile.exists()) {
          configuration.readCacheConfiguration(new FileInputStream(cacheConfigurationFile),
              defaultCacheDir.toString());
        } else {
          configuration.setCachingDefaults(defaultCacheDir.toString());
        }

      } catch (Exception e) {
        throw new InitializationError(String.format("Invalid config file  %s", config), e);
      }

      cacheCleanupExecutor.initialize(configuration);

      if (fullCacheClean) {
        try {
          cacheCleanupExecutor.fullCacheCleanup();
        } catch (Exception e) {
          throw new InitializationError("Failed to perform full cache cleanup", e);
        }
      }

      this.initialized = true;
      logger.info(String.format("buildcache: %s", buildCacheDisabled ? "disabled" : "enabled"));

    } catch (InitializationError e) {
      error = true;
      errorMessage = String.format("buildcache initialization failed: %s", e.getMessage());
      logger.error(e.getMessage(), e.getCause());
    }
  }

  @Override
  public boolean isBuildCacheDisabled() {
    return buildCacheDisabled;
  }

  @Override
  public boolean isBuildCacheDebug() {
    return buildCacheDebug;
  }

  @Override
  public boolean isBuildCacheProfile() {
    return buildCacheProfile;
  }

  @Override
  public void cleanProject(MavenSession session) {
    MavenProject project = session.getCurrentProject();
    try {
      File projectCacheDir = getProjectCacheDirectory(project).toFile();
      cacheCleanupExecutor.projectCacheCleanup(projectCacheDir);

    } catch (Exception e) {
      logger.error(String.format("buildcache: failed to perform cache cleanup for %s",
          getProjectId(project)), e);
    }
  }

  @Override
  public ProjectBuildStatus getProjectStatus(MavenSession session) {
    return getProjectStatus(session, null);
  }

  @Override
  public ProjectBuildStatus getProjectStatus(MavenSession session, MojoExecution mojoExecution) {

    MavenProject project = session.getCurrentProject();
    String key = getProjectId(project);
    ProjectBuildStatus projectStatus =
        projectStatusMap.computeIfAbsent(key, k -> configure(new ProjectBuildStatus(), session));

    if (mojoExecution != null && MojoExecUtil.isCompileRelatedPhase(mojoExecution)) {
      configureMainCompile(projectStatus, session);

    } else if (mojoExecution != null && MojoExecUtil.isTestCompileRelatedPhase(mojoExecution)) {
      configureTestCompile(projectStatus, session);

    } else if (mojoExecution != null && MojoExecUtil.isTestRelatedPhase(mojoExecution)) {
      configureTest(projectStatus, session);

    } else if (mojoExecution != null && MojoExecUtil.isIntegrationTestRelatedPhase(mojoExecution)) {
      configureIntegrationTest(projectStatus, session);
    }

    return projectStatus;
  }

  @Override
  public Optional<ProjectBuildStatus> getProjectStatus(String groupId, String artifactId) {
    String key = String.format("%s:%s", groupId, artifactId);
    return Optional.ofNullable(projectStatusMap.get(key));
  }

  @Override
  public boolean useCachedMainClasses(MavenSession session) {
    Path zipFile = getMainClassesZipFile(session);
    return unzipCachedClasses(session, zipFile, CLASSES_DIR);
  }

  @Override
  public boolean useCachedTestClasses(MavenSession session) {
    Path zipFile = getTestClassesZipFile(session);
    return unzipCachedClasses(session, zipFile, TEST_CLASSES_DIR);
  }

  @Override
  public boolean isTestExecutionCacheHit(MavenSession session) {
    Path testCacheFile = getTestCacheFile(session);
    return checkTestExecutionCacheHit(testCacheFile);
  }

  @Override
  public boolean isIntegrationTestExecutionCacheHit(MavenSession session) {
    Path testCacheFile = getIntegrationTestCacheFile(session);
    return checkTestExecutionCacheHit(testCacheFile);
  }

  private boolean checkTestExecutionCacheHit(Path testCacheFile) {
    boolean cacheHit = fileUtil.fileExists(testCacheFile);
    if (!cacheHit) {
      return false;
    }
    if (checkBuildCacheIgnore(testCacheFile)) {
      return false;
    }

    testCacheFile.toFile().setLastModified(System.currentTimeMillis());
    return true;
  }

  private boolean unzipCachedClasses(MavenSession session, Path zipFile, String classesDir) {
    boolean cacheHit = fileUtil.fileExists(zipFile);
    if (!cacheHit) {
      return false;
    }
    if (checkBuildCacheIgnore(zipFile)) {
      return false;
    }

    Path classesPath = Paths.get(session.getCurrentProject().getBuild().getDirectory(), classesDir);
    fileUtil.mkdirs(classesPath);

    try {
      fileUtil.unzip(zipFile, classesPath);
    } catch (Exception e) {
      logger.error("buildcache: could not extract classes from cache zip file", e);
      return false;
    }
    zipFile.toFile().setLastModified(System.currentTimeMillis());
    return true;
  }

  private boolean checkBuildCacheIgnore(Path cacheFile) {
    if (!buildCacheIgnore) {
      return false;
    }
    logger.info(String.format("buildcache: deleting cache entry: %s", cacheFile));
    fileUtil.deleteFile(cacheFile);
    return true;
  }

  @Override
  public void cacheMainClasses(MavenSession session) {
    Path zipFile = getMainClassesZipFile(session);
    createClassesZipFile(session, zipFile, CLASSES_DIR);
  }

  @Override
  public void cacheTestClasses(MavenSession session) {
    Path zipFile = getTestClassesZipFile(session);
    createClassesZipFile(session, zipFile, TEST_CLASSES_DIR);
  }

  @Override
  public void cacheTestExecution(MavenSession session) {
    Path testCacheFile = getTestCacheFile(session);
    createFile(testCacheFile, "");
  }

  @Override
  public void cacheIntegrationTestExecution(MavenSession session) {
    Path testCacheFile = getIntegrationTestCacheFile(session);
    createFile(testCacheFile, "");
  }

  private void createClassesZipFile(MavenSession session, Path zipFile, String classesDir) {
    Path classesPath = Paths.get(session.getCurrentProject().getBuild().getDirectory(), classesDir);
    String[] classFiles = fileUtil.scanClassFilesFromDirectory(classesPath);
    try {
      fileUtil.createZipFile(zipFile, classesPath, classFiles);
    } catch (Exception e) {
      logger.error("buildcache: failed to create class zip file", e);
      try {
        fileUtil.deleteFile(zipFile);
      } catch (Exception ee) {
      }
    }
  }

  @Override
  public void writeMainCompileDetails(MavenSession session) {
    Path mainCompileDetailsFile = getMainCompileDetailsFile(session);
    createFile(mainCompileDetailsFile,
        getProjectStatus(session).getMainCompile().getPhaseDetails());
  }

  @Override
  public void writeTestCompileDetails(MavenSession session) {
    Path testCompileDetailsFile = getTestCompileDetailsFile(session);
    createFile(testCompileDetailsFile,
        getProjectStatus(session).getTestCompile().getPhaseDetails());
  }

  @Override
  public void writeTestExecutionDetails(MavenSession session) {
    Path testDetailsFile = getTestDetailsFile(session);
    createFile(testDetailsFile, getProjectStatus(session).getTest().getPhaseDetails());
  }

  @Override
  public void writeIntegrationTestExecutionDetails(MavenSession session) {
    Path testDetailsFile = getIntegrationTestDetailsFile(session);
    createFile(testDetailsFile, getProjectStatus(session).getIntegrationTest().getPhaseDetails());
  }

  private void createFile(Path path, String content) {
    try {
      fileUtil.writeFile(path, content);
    } catch (IOException e) {
      logger.error("buildcache: failed create file " + path, e);
    }
  }

  private Path getMainClassesZipFile(MavenSession session) {
    String mainCompileHash = getProjectStatus(session).getMainCompile().getPhaseHash();
    String filename = String.format("classes-%s.zip", mainCompileHash);
    return getProjectCacheDirectory(session.getCurrentProject()).resolve(filename);
  }

  private Path getMainCompileDetailsFile(MavenSession session) {
    String mainCompileHash = getProjectStatus(session).getMainCompile().getPhaseHash();
    String filename = String.format("classes-%s.txt", mainCompileHash);
    return getProjectCacheDirectory(session.getCurrentProject()).resolve(filename);
  }

  private Path getTestClassesZipFile(MavenSession session) {
    String testCompileHash = getProjectStatus(session).getTestCompile().getPhaseHash();
    String filename = String.format("test-classes-%s.zip", testCompileHash);
    return getProjectCacheDirectory(session.getCurrentProject()).resolve(filename);
  }

  private Path getTestCompileDetailsFile(MavenSession session) {
    String testCompileHash = getProjectStatus(session).getTestCompile().getPhaseHash();
    String filename = String.format("test-classes-%s.txt", testCompileHash);
    return getProjectCacheDirectory(session.getCurrentProject()).resolve(filename);
  }

  private Path getTestCacheFile(MavenSession session) {
    String testCompileHash = getProjectStatus(session).getTest().getPhaseHash();
    String filename = String.format("test-%s.ok", testCompileHash);
    return getProjectCacheDirectory(session.getCurrentProject()).resolve(filename);
  }

  private Path getTestDetailsFile(MavenSession session) {
    String testCompileHash = getProjectStatus(session).getTest().getPhaseHash();
    String filename = String.format("test-%s.txt", testCompileHash);
    return getProjectCacheDirectory(session.getCurrentProject()).resolve(filename);
  }

  private Path getIntegrationTestCacheFile(MavenSession session) {
    String itTestCompileHash = getProjectStatus(session).getIntegrationTest().getPhaseHash();
    String filename = String.format("it-test-%s.ok", itTestCompileHash);
    return getProjectCacheDirectory(session.getCurrentProject()).resolve(filename);
  }

  private Path getIntegrationTestDetailsFile(MavenSession session) {
    String itTestCompileHash = getProjectStatus(session).getIntegrationTest().getPhaseHash();
    String filename = String.format("it-test-%s.txt", itTestCompileHash);
    return getProjectCacheDirectory(session.getCurrentProject()).resolve(filename);
  }

  private Path getProjectCacheDirectory(MavenProject project) {
    return Paths.get(configuration.getCacheDirectory())
        .resolve(Paths.get("", project.getGroupId().split("\\."))).resolve(project.getArtifactId());
  }

  private ProjectBuildStatus configure(ProjectBuildStatus projectStatus, MavenSession session) {
    try {
      PluginParameterExpressionEvaluator expressionEvaluator =
          expressionEvaluatorProvider.apply(session);

      projectStatus.setMavenMainSkip(checkProperty(expressionEvaluator, MAVEN_MAIN_SKIP));
      projectStatus.setMavenTestSkip(checkProperty(expressionEvaluator, MAVEN_TEST_SKIP));
      projectStatus.setSkipTests(checkProperty(expressionEvaluator, SKIP_TESTS));
      projectStatus.setSkipItTests(checkProperty(expressionEvaluator, SKIP_IT_TESTS));
      projectStatus.setTestSubset(nonEmptyPropertyValue(expressionEvaluator, TEST_SUBSET));
      projectStatus.setItTestSubset(nonEmptyPropertyValue(expressionEvaluator, IT_TEST_SUBSET));
      projectStatus.setBuildCacheEnabled(!checkProperty(expressionEvaluator, BUILD_CACHE_DISABLE));

      MavenExecutionPlan plan = lifecycleExecutor.calculateExecutionPlan(session, "verify");
      projectStatus.setMavenExecutionPlan(plan);

    } catch (Exception e) {
      logger.error(String.format(
          "buildcache: failed to configure project, buildcache extension is disabled for %s",
          getProjectId(session.getCurrentProject())), e);
      projectStatus.setBuildCacheEnabled(false);
    }
    return projectStatus;
  }

  private void configureMainCompile(ProjectBuildStatus projectStatus, MavenSession session) {
    configure(projectStatus, projectStatus.getMainCompile(), session, () -> {
      hashUtil.setProjectCompilePhaseDetails(projectStatus, this, session.getCurrentProject(),
          configuration.getMainCompileTriggers(session.getCurrentProject()),
          this.compilePhaseProperties);
    });
  }

  private void configureTestCompile(ProjectBuildStatus projectStatus, MavenSession session) {
    configure(projectStatus, projectStatus.getTestCompile(), session, () -> {
      hashUtil.setProjectTestCompilePhaseDetails(projectStatus, this, session.getCurrentProject(),
          configuration.getTestCompileTriggers(session.getCurrentProject()),
          this.compilePhaseProperties);
    });
  }

  private void configureTest(ProjectBuildStatus projectStatus, MavenSession session) {
    configure(projectStatus, projectStatus.getTest(), session, () -> {
      hashUtil.setProjectTestPhaseDetails(projectStatus, session.getCurrentProject(),
          configuration.getTestExecutionTriggers(session.getCurrentProject()));
    });
  }

  private void configureIntegrationTest(ProjectBuildStatus projectStatus, MavenSession session) {
    configure(projectStatus, projectStatus.getIntegrationTest(), session, () -> {
      hashUtil.setProjectIntegrationTestPhaseDetails(projectStatus, session.getCurrentProject(),
          configuration.getIntegrationTestExecutionTriggers(session.getCurrentProject()));
    });
  }

  private void configure(ProjectBuildStatus projectStatus,
      ProjectBuildStatus.Phase currentProjectStatusPhase, MavenSession session,
      CheckedRunnable configurator) {

    if (currentProjectStatusPhase.isConfigured() || projectStatus.isBuildCacheDisabled()) {
      return;
    }

    try {
      configurator.run();
      currentProjectStatusPhase.setConfigured(true);

    } catch (Exception e) {
      logger.error(String.format(
          "buildcache: failed to configure %s phase details, buildcache extension is disabled for %s",
          currentProjectStatusPhase.getName(), getProjectId(session.getCurrentProject())), e);
      projectStatus.setBuildCacheEnabled(false);
    }
  }

  void setLogger(Logger logger) {
    this.logger = logger;
  }

  void setLifecycleExecutor(LifecycleExecutor lifecycleExecutor) {
    this.lifecycleExecutor = lifecycleExecutor;
  }

  void setExpressionEvaluatorProvider(
      Function<MavenSession, PluginParameterExpressionEvaluator> expressionEvaluatorProvider) {
    this.expressionEvaluatorProvider = expressionEvaluatorProvider;
  }

  void setFileUtil(FileUtil fileUtil) {
    this.fileUtil = fileUtil;
  }

  void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  void setCacheCleanupExecutor(CacheCleanupExecutor cacheCleanupExecutor) {
    this.cacheCleanupExecutor = cacheCleanupExecutor;
  }

  void setBuildCacheIgnore(boolean buildCacheIgnore) {
    this.buildCacheIgnore = buildCacheIgnore;
  }

  static boolean nonEmptyPropertyValue(PluginParameterExpressionEvaluator expressionEvaluator,
      String property) throws ExpressionEvaluationException {
    String value = evaluate(expressionEvaluator, property);
    return value == null ? false : !value.trim().isEmpty();
  }

  static boolean checkProperty(PluginParameterExpressionEvaluator expressionEvaluator,
      String property) throws ExpressionEvaluationException {
    return "true".equalsIgnoreCase(evaluate(expressionEvaluator, property));
  }

  static String evaluate(PluginParameterExpressionEvaluator expressionEvaluator, String property)
      throws ExpressionEvaluationException {
    Object value = expressionEvaluator.evaluate("${" + property + "}");
    return value == null ? null : value.toString();
  }

  private static class InitializationError extends Exception {
    private static final long serialVersionUID = 1L;

    InitializationError(String message, Exception cause) {
      super(message, cause);
    }
  }

  private static interface CheckedRunnable {
    void run() throws Exception;
  }
}
