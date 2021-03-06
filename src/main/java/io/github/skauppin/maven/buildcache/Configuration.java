package io.github.skauppin.maven.buildcache;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class Configuration {

  private static final String SCHEMA_PROJECT_CONFIG = "/buildcache-project.xsd";
  private static final String SCHEMA_CACHE_CONFIG = "/buildcache-cache.xsd";

  private static final Integer DEFAULT_PROJECT_CACHE_MAX_SIZE_MB = null;
  private static final Integer DEFAULT_PROJECT_CACHE_MAX_ENTRIES = 20;
  private static final Duration DEFAULT_PROJECT_CACHE_MAX_AGE = Duration.ofDays(90);
  private static final Integer DEFAULT_TOTAL_CACHE_MAX_SIZE_MB = null;

  private static final String CONFIGURATION_ELEMENT = "configuration";
  private static final String CACHEDIR_ELEMENT = "cache-directory";
  private static final String PROJECT_CACHE_MAX_SIZE_ELEMENT = "project-cache-max-size";
  private static final String PROJECT_CACHE_MAX_ENTRIES_ELEMENT = "project-cache-max-entries";
  private static final String PROJECT_CACHE_MAX_AGE_ELEMENT = "project-cache-max-age";
  private static final String TOTAL_CACHE_MAX_SIZE_ELEMENT = "total-cache-max-size";

  private static final Pattern MEGABYTE_PATTERN =
      Pattern.compile("\\s*([0-9]+)\\s*(M|MB)?\\s*", Pattern.CASE_INSENSITIVE);

  private static final Pattern NUMBER_PATTERN =
      Pattern.compile("\\s*([0-9]+)\\s*", Pattern.CASE_INSENSITIVE);

  private static final String PROJECTS_ELEMENT = "projects";
  private static final String PROJECT_ELEMENT = "project";
  private static final String PROJECT_ID = "id";

  private static final String MAIN_COMPILE = "compile-triggers";
  private static final String TEST_COMPILE = "test-compile-triggers";
  private static final String TEST_EXECUTION = "test-triggers";
  private static final String INTEGRATION_TEST_EXECUTION = "integration-test-triggers";

  private static final String FILESET_ELEMENT = "fileset";
  private static final String DIRECTORY_ELEMENT = "directory";
  private static final String SYMLINKS_ELEMENT = "follow-symlinks";
  private static final String DEFAULT_EXCLUDES_ELEMENT = "use-default-excludes";
  private static final String INCLUDE_ELEMENT = "include";
  private static final String EXCLUDE_ELEMENT = "exclude";

  private final Map<String, List<FileSet>> projectPathMap = new LinkedHashMap<>();

  private String cacheDirectory;
  private Integer projectCacheMaxSizeMb;
  private Integer projectCacheMaxEntries;
  private Duration projectCacheMaxAge;
  private Integer totalCacheMaxSizeMb;

  public void readCacheConfiguration(InputStream inputStream, String defaultCacheDir)
      throws ParserConfigurationException, SAXException, IOException {
    Element buildCacheElement = parseConfigurationFile(inputStream, SCHEMA_CACHE_CONFIG);
    Element configurationElement = getFirstChildElement(buildCacheElement, CONFIGURATION_ELEMENT);
    if (configurationElement != null) {
      readCacheProperties(configurationElement);
    }
    if (this.cacheDirectory == null) {
      this.cacheDirectory = defaultCacheDir;
    }
  }

  public void readProjectConfiguration(InputStream inputStream)
      throws ParserConfigurationException, SAXException, IOException {
    Element buildCacheElement = parseConfigurationFile(inputStream, SCHEMA_PROJECT_CONFIG);
    Element projectsElement = getFirstChildElement(buildCacheElement, PROJECTS_ELEMENT);
    if (projectsElement != null) {
      readProjects(projectsElement);
    }
  }

  private Element parseConfigurationFile(InputStream inputStream, String schemaResource)
      throws ParserConfigurationException, SAXException, IOException {

    SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    Schema schema = sf.newSchema(Configuration.class.getResource(schemaResource));

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setSchema(schema);

    DocumentBuilder db = dbf.newDocumentBuilder();
    db.setErrorHandler(new XMLErrorHandler());
    Document doc = db.parse(inputStream);

    return doc.getDocumentElement();
  }

  private void readCacheProperties(Element configurationElement) {
    this.cacheDirectory = getChildTextContent(configurationElement, CACHEDIR_ELEMENT);
    this.projectCacheMaxSizeMb =
        parseMegabytes(getChildTextContent(configurationElement, PROJECT_CACHE_MAX_SIZE_ELEMENT));
    this.projectCacheMaxEntries =
        parseNumber(getChildTextContent(configurationElement, PROJECT_CACHE_MAX_ENTRIES_ELEMENT));
    this.projectCacheMaxAge =
        parseDuration(getChildTextContent(configurationElement, PROJECT_CACHE_MAX_AGE_ELEMENT));
    this.totalCacheMaxSizeMb =
        parseMegabytes(getChildTextContent(configurationElement, TOTAL_CACHE_MAX_SIZE_ELEMENT));
  }

  public void setCachingDefaults(String defaultCacheDir) {
    this.cacheDirectory = defaultCacheDir;
    this.projectCacheMaxSizeMb = DEFAULT_PROJECT_CACHE_MAX_SIZE_MB;
    this.projectCacheMaxEntries = DEFAULT_PROJECT_CACHE_MAX_ENTRIES;
    this.projectCacheMaxAge = DEFAULT_PROJECT_CACHE_MAX_AGE;
    this.totalCacheMaxSizeMb = DEFAULT_TOTAL_CACHE_MAX_SIZE_MB;
  }

  private void readProjects(Element projectsElement) {
    NodeList projects = projectsElement.getElementsByTagName(PROJECT_ELEMENT);
    for (int i = 0; i < projects.getLength(); i++) {
      Element projectElement = (Element) projects.item(i);
      String projectId = projectElement.getAttribute(PROJECT_ID);
      handleTriggers(projectElement, MAIN_COMPILE, mapKey(MAIN_COMPILE, projectId));
      handleTriggers(projectElement, TEST_COMPILE, mapKey(TEST_COMPILE, projectId));
      handleTriggers(projectElement, TEST_EXECUTION, mapKey(TEST_EXECUTION, projectId));
      handleTriggers(projectElement, INTEGRATION_TEST_EXECUTION,
          mapKey(INTEGRATION_TEST_EXECUTION, projectId));
    }
  }

  private String mapKey(String p1, String p2) {
    return String.format("%s-%s", p1, p2);
  }

  private void handleTriggers(Element buildCacheElement, String triggerElementName, String mapKey) {

    Element triggerElement = getFirstChildElement(buildCacheElement, triggerElementName);
    if (triggerElement == null) {
      return;
    }

    NodeList filesets = triggerElement.getElementsByTagName(FILESET_ELEMENT);
    for (int i = 0; i < filesets.getLength(); i++) {
      Element filesetElement = (Element) filesets.item(i);
      String directory = getChildTextContent(filesetElement, DIRECTORY_ELEMENT);
      if (directory == null) {
        throw new ConfigurationException(
            String.format("Invalid XML configuration: <%s> has empty value", DIRECTORY_ELEMENT));
      }
      boolean followSymlinks =
          "true".equalsIgnoreCase(getChildTextContent(filesetElement, SYMLINKS_ELEMENT));
      boolean useDefaultExcludes =
          !"false".equalsIgnoreCase(getChildTextContent(filesetElement, DEFAULT_EXCLUDES_ELEMENT));
      List<String> includes = getPaths(filesetElement, INCLUDE_ELEMENT);
      List<String> excludes = getPaths(filesetElement, EXCLUDE_ELEMENT);

      FileSet fileSet = new FileSet();
      fileSet.setDirectory(directory);
      fileSet.setFollowSymlinks(followSymlinks);
      fileSet.setUseDefaultExcludes(useDefaultExcludes);
      fileSet.setIncludes(includes);
      fileSet.setExcludes(excludes);

      projectPathMap.computeIfAbsent(mapKey, k -> new ArrayList<>()).add(fileSet);
    }
  }

  private List<String> getPaths(Element element, String childName) {
    List<String> list = new ArrayList<>();
    NodeList nodes = element.getElementsByTagName(childName);
    for (int i = 0; i < nodes.getLength(); i++) {
      String value = getTextContent((Element) nodes.item(i));
      if (value == null) {
        continue;
      }
      list.add(value);
    }
    return list;
  }

  private String getTextContent(Element e) {
    String value = e.getTextContent().trim();
    return value.isEmpty() ? null : value;
  }

  private String getChildTextContent(Element node, String childElementName) {
    Element childElement = getFirstChildElement(node, childElementName);
    return childElement != null ? getTextContent(childElement) : null;
  }

  private Element getFirstChildElement(Element node, String childElementName) {
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE
          && childElementName.equals(child.getNodeName())) {
        return (Element) child;
      }
    }
    return null;
  }

  public String getCacheDirectory() {
    return cacheDirectory;
  }

  public boolean hasProjectCacheMaxSizeMb() {
    return projectCacheMaxSizeMb != null;
  }

  public Integer getProjectCacheMaxSizeMb() {
    return projectCacheMaxSizeMb;
  }

  public boolean hasProjectCacheMaxEntries() {
    return projectCacheMaxEntries != null;
  }

  public Integer getProjectCacheMaxEntries() {
    return projectCacheMaxEntries;
  }

  public boolean hasProjectCacheMaxAge() {
    return projectCacheMaxAge != null;
  }

  public Duration getProjectCacheMaxAge() {
    return projectCacheMaxAge;
  }

  public boolean hasTotalCacheMaxSizeMb() {
    return totalCacheMaxSizeMb != null;
  }

  public Integer getTotalCacheMaxSizeMb() {
    return totalCacheMaxSizeMb;
  }

  List<FileSet> getMainCompileTriggers(MavenProject project) {
    return relativize(getOrEmpty(MAIN_COMPILE, project), project);
  }

  List<FileSet> getTestCompileTriggers(MavenProject project) {
    return relativize(getOrEmpty(TEST_COMPILE, project), project);
  }

  List<FileSet> getTestExecutionTriggers(MavenProject project) {
    return relativize(getOrEmpty(TEST_EXECUTION, project), project);
  }

  List<FileSet> getIntegrationTestExecutionTriggers(MavenProject project) {
    return relativize(getOrEmpty(INTEGRATION_TEST_EXECUTION, project), project);
  }

  int getProjectPathMapSize() {
    return projectPathMap.size();
  }

  private List<FileSet> relativize(List<FileSet> filesets, MavenProject project) {
    return filesets.stream().map(f -> {
      FileSet set = new FileSet();
      set.setDirectory(project.getBasedir().toPath().resolve(f.getDirectory()).toString());
      set.setFollowSymlinks(f.isFollowSymlinks());
      set.setUseDefaultExcludes(f.isUseDefaultExcludes());
      set.setIncludes(f.getIncludes());
      set.setExcludes(f.getExcludes());
      return set;
    }).collect(Collectors.toList());
  }

  private List<FileSet> getOrEmpty(String keyPart1, MavenProject project) {
    String key = mapKey(keyPart1, BuildCache.getProjectId(project));
    if (!projectPathMap.containsKey(key)) {
      key = mapKey(keyPart1, project.getArtifactId());
    }
    return projectPathMap.getOrDefault(key, Collections.emptyList());
  }

  static Integer parseMegabytes(String str) {
    return parse(MEGABYTE_PATTERN, str, "size");
  }

  static Integer parseNumber(String str) {
    return parse(NUMBER_PATTERN, str, "number");
  }

  static Duration parseDuration(String str) {
    if (str == null) {
      return null;
    }

    try {
      return Duration.parse(str.trim()).abs();
    } catch (Exception e) {
    }

    return Duration.ofDays(parse(NUMBER_PATTERN, str, "number or duration"));
  }

  static Integer parse(Pattern pattern, String str, String errorMessageValueName) {
    if (str == null) {
      return null;
    }
    Matcher m = pattern.matcher(str);
    if (!m.matches()) {
      throw new ConfigurationException(String
          .format("Invalid XML configuration: '%s' is not valid %s", str, errorMessageValueName));
    }
    return Integer.parseInt(m.group(1));
  }

  public static class ConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
      super(message);
    }
  }

  public static class XMLErrorHandler implements ErrorHandler {

    @Override
    public void error(SAXParseException e) throws SAXException {
      throw e;
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
      throw e;
    }

    @Override
    public void warning(SAXParseException e) throws SAXException {}

  }
}
