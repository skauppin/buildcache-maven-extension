package io.github.skauppin.maven.buildcache;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.maven.lifecycle.MavenExecutionPlan;

public class ProjectBuildStatus {

  private boolean buildCacheEnabled = true;
  private boolean mavenMainSkip = false;
  private boolean mavenTestSkip = false;
  private boolean skipTests = false;
  private boolean skipItTests = false;
  private boolean testSubset = false;
  private boolean itTestSubset = false;

  private transient MavenExecutionPlan mavenExecutionPlan = null;

  private final Phase mainCompile = new Phase("compile");
  private final Phase testCompile = new Phase("test-compile");
  private final Phase test = new Phase("test");
  private final Phase integrationTest = new Phase("integration-test");

  public boolean isBuildCacheEnabled() {
    return buildCacheEnabled;
  }

  public boolean isBuildCacheDisabled() {
    return !buildCacheEnabled;
  }

  public void setBuildCacheEnabled(boolean buildCacheEnabled) {
    this.buildCacheEnabled = buildCacheEnabled;
  }

  public boolean isMavenMainSkip() {
    return mavenMainSkip;
  }

  public void setMavenMainSkip(boolean mavenMainSkip) {
    this.mavenMainSkip = mavenMainSkip;
  }

  public boolean isMavenTestSkip() {
    return mavenTestSkip;
  }

  public void setMavenTestSkip(boolean mavenTestSkip) {
    this.mavenTestSkip = mavenTestSkip;
  }

  public boolean isSkipTests() {
    return skipTests;
  }

  public void setSkipTests(boolean skipTests) {
    this.skipTests = skipTests;
  }

  public boolean isSkipItTests() {
    return skipItTests;
  }

  public void setSkipItTests(boolean skipItTests) {
    this.skipItTests = skipItTests;
  }

  public boolean isTestSubset() {
    return testSubset;
  }

  public void setTestSubset(boolean testSubset) {
    this.testSubset = testSubset;
  }

  public boolean isItTestSubset() {
    return itTestSubset;
  }

  public void setItTestSubset(boolean itTestSubset) {
    this.itTestSubset = itTestSubset;
  }

  public MavenExecutionPlan getMavenExecutionPlan() {
    return mavenExecutionPlan;
  }

  public void setMavenExecutionPlan(MavenExecutionPlan mavenExecutionPlan) {
    this.mavenExecutionPlan = mavenExecutionPlan;
  }

  public Phase getMainCompile() {
    return mainCompile;
  }

  public Phase getTestCompile() {
    return testCompile;
  }

  public Phase getTest() {
    return test;
  }

  public Phase getIntegrationTest() {
    return integrationTest;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.NO_CLASS_NAME_STYLE);
  }

  public static class Phase {

    private final String name;

    private boolean configured = false;
    private boolean visited = false;
    private boolean cacheHit = false;
    private transient String phadeDetails = null;
    private String phaseHash = null;

    public Phase(String name) {
      this.name = name;
    }

    public String getName() {
      return this.name;
    }

    public boolean setVisited() {
      this.visited = true;
      return true;
    }

    public boolean setCacheHit() {
      this.cacheHit = true;
      return true;
    }

    public boolean isVisited() {
      return this.visited;
    }

    public boolean notVisited() {
      return !this.visited;
    }

    public boolean isCacheHit() {
      return this.cacheHit;
    }

    public String getPhaseDetails() {
      return phadeDetails;
    }

    public void setPhaseDetails(String phadeDetails) {
      this.phadeDetails = phadeDetails;
    }

    public String getPhaseHash() {
      return phaseHash;
    }

    public void setPhaseHash(String phaseHash) {
      this.phaseHash = phaseHash;
    }

    public boolean isConfigured() {
      return configured;
    }

    public void setConfigured(boolean configured) {
      this.configured = configured;
    }

    @Override
    public String toString() {
      return ReflectionToStringBuilder.toString(this, ToStringStyle.NO_CLASS_NAME_STYLE);
    }
  }
}
