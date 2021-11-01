# Buildcache Maven Extension
*Faster development builds by caching compiled classes and test execution results*

### Requirements
* Maven `3.3.1` or newer
* Java `8` or newer

### Disclaimer

The buildcache extension is still experimental and not extensively tested with different kinds of Maven projects.

# Quick Guide

For many projects all that is required is to declare the extension in `[project-root]/.mvn/extensions.xml`

```xml
<extensions>
  <extension>
    <groupId>io.github.skauppin.maven</groupId>
    <artifactId>buildcache-maven-extension</artifactId>
    <version>0.0.2</version>
  </extension>
</extensions>
```

That said, to have the extension working correctly for your project it's important to know how it works. *You may need additional configuration for your project.*

Then building the project, for example `mvn clean verify`, you'll get the normal Maven output with some additional lines telling you that classes and test execution results have been cached. For a multi-module project caching happens for each module separately. Caching is not done for a failed module or project.

```
[INFO] buildcache: enabled []
...
[INFO] buildcache: caching main classes (ad320a1fb350ca665e1042f147645d3a)
[INFO] buildcache: caching test classes (cc6dc6f89f980a1b9c5ce7bc80c5598b)
[INFO] buildcache: caching test execution (2f66b504bea17f72f8f0caeb2fd5f92c)
[INFO] buildcache: caching it-test execution (13e09db379a1e214f8217a365244511b)
```

Building the project again makes use of the cached data

```
[INFO] buildcache: enabled []
...
[INFO] --- maven-compiler-plugin:3.8.0:compile (default-compile) ---
[INFO] buildcache: cache hit - using cached main classes (ad320a1fb350ca665e1042f147645d3a)

[INFO] --- maven-compiler-plugin:3.8.0:testCompile (default-testCompile) ---
[INFO] buildcache: cache hit - using cached test classes (cc6dc6f89f980a1b9c5ce7bc80c5598b)

[INFO] --- maven-surefire-plugin:3.0.0-M5:test (default-test) ---
[INFO] buildcache: cache hit - test execution success (2f66b504bea17f72f8f0caeb2fd5f92c)
```
### WARNING

You should not use the buildcache when doing release builds. If the extension is enabled in your release build environment (for example when having `.mvn/extensions.xml` in version control), make sure to disable the extension using `-Dbuildcache.disable` parameter when building a release

```
$> mvn clean verify -Dbuildcache.disable
```

# Complete Guide
This documentation provides information about how the Buildcache extension works and how to configure it.

### Overview - How Does It Work?

Buildcache is a Maven extension. It participates in the build lifecycle by caching compiled classes and test execution results, and skips execution of specific build phases on subsequent runs if cached data is available.

To detect whether cached data is available for a build phase the extension calculates a hash value of relavant project details and does a cache lookup using the hash value as a key. If you have executed the build before and none of the relevant details have not changed then cached data is found and being used instead of executing the build phase. But in case some of the relevant details have changed then the hash value changes and the new value doesn't match to cached data, which results in executing the build phase and, in case of successful build, caching the new data.

There are basically four different phases in the build which are affected by the Buildcache extension: *compiling sources*, *compiling test sources*, *executing unit tests*, and *executing integration tests*. Both of the compile phases are handled in a similar manner and result in caching compiled (and possibly processed) `.class` files. Both of the test phases are also handled similarly and result in caching a note that test execution has succeeded. Caching happens only for successful build.

The table below describes the functionality briefly for each build phase

| Phase             | Hashed data | Cached data | Skipped Maven build phases on cache hit |
| ----------------- | ----------- | ----------- | -------------------- |
| Compile           | sources, dependencies, selected properties, plugins, *additional triggers** | main `.class` files | `compile`, `process-classes`
| Test compile      | test sources, dependencies, selected properties, plugins, *additional triggers** | test `.class` files | `test-compile`, `process-test-classes`
| Test              | Compile hash, Test compile hash, resource files, test resource files, plugins, *additional triggers**| *test execution success*** | `test`
| Integration test  | Compile hash, Test compile hash, resource files, test resource files, plugins, *additional triggers**        | *test execution success*** | `pre-integration-test`, `integration-test`, `post-integration-test`

*additional triggers** -- user configured filesets (see [Configuration](#project-specific-configuration))

*test execution success*** -- no project data cached, but just information that tests or integration tests passed

### Special Maven Build Properties

There are some Maven build properties that affect Buildcache behaviour.

| Element             | Description | Buildcache behavior |
| --------------------| ----------- | ------------------- |
| `maven.main.skip`   | Compiling main classes skipped | Buildcache disabled for the build |
| `maven.test.skip`   | Compiling and running test skipped | Test classes, unit test execution result, and integration test result won't be cached |
| `skipTests`         | Tests not executed | Unit test execution result and integration test result won't be cached |
| `skipITs`           | Integration tests not executed | Integration test result won't be cached |
| `test=[PATTERN]`    | Subset of unit tests executed | Test result won't be cached |
| `it.test=[PATTERN]` | Subset of integration tests executed | Ingeration test result won't be cached |

### Cache Location

Default cache location is `[user.home]/.m2/buildcache` but another directory can be configured (see [Configuration](#global-cache-configuration))

### Configuration

#### Enabling the Extension

Enable the extension for a project by declaring it in `[project-root]/.mvn/extensions.xml`

```xml
<extensions>
  <extension>
    <groupId>io.github.skauppin.maven</groupId>
    <artifactId>buildcache-maven-extension</artifactId>
    <version>0.0.2</version>
  </extension>
</extensions>
```

Declaring the extension in `pom.xml` won't work as the extension doesn't get initialized properly.

#### Project Specific Configuration

Project specific configuration makes it possible to include files outside the standard directory structure to hash value calculation, and to exclude files on standard directory structure from the hash calculation. This provides means to make the cache work correctly for a project and, on the other hand, to optimize the cache usage.

Project specific configuration is declared in `[project-root]/.mvn/buildcache.xml` and has the following syntax

```xml
<buildcache>
  <projects>
    <!-- one or more <project> elements -->
    <project id="PROJECT_ID">
      <compile-triggers>
        <!-- one or more <fileset> elements -->
      </compile-triggers>
      <test-compile-triggers>
        <!-- one or more <fileset> elements -->
      </test-compile-triggers>
      <test-triggers>
        <!-- one or more <fileset> elements -->
      </test-triggers>
      <integration-test-triggers>
        <!-- one or more <fileset> elements -->
      </integration-test-triggers>
    </project>
  </projects>
</buildcache>
```
where `PROJECT_ID` is `[groupId]:[artifactId]`, or just `[artifactId]`. Each of the `<...-triggers>` elements is optional.

**FileSet**
A `<fileset>` element is used for matching files from a given base directory. Syntax is
```xml
<fileset>
  <directory>[PATH]</directory>                       <!-- mandatory -->
  <follow-symlinks>false</follow-symlinks>            <!-- optional -->
  <use-default-excludes>true</use-default-excludes>   <!-- optional -->
  <includes>                                          <!-- optional -->
    <!-- one or more <include> elements -->
    <include>[PATTERN]</include>
  </includes>
  <excludes>                                          <!-- optional -->
    <!-- one or more <exclude> elements -->
    <exclude>[PATTERN]</exclude>
  </excludes>
</fileset>
```
where
* `[PATH]` is absolute or relative path. Relative paths are resolved against project/module root directory.
* `[PATTERN]` is a standard Maven FileSet pattern and is relative to `[PATH]`.
* `follow-symlinks` -- specifies whether symbolic links should be traversed, or handled as-is. Default is `false`
* `use-default-excludes` -- whether to include exclusion patterns for common temporary and SCM control files. Default is `true`

Default include pattern is `**`, meaning that all files from a directory will be included.

Using a `[PATH]` that matches one of the standard directories included for hash calculation will override the default fileset. When configuring filesets it's recommended to verify the matched files using `-Dbuildcache.debug` flag (see [Debugging](#debugging))

**Example**

The below project configuration declares a fileset to be included in the test execution hash calculation. This means that test execution is triggered if any of the files matched by the fileset have changed.

```xml
<buildcache>
  <projects>
    <project id="com.test:test-lib">
      <test-triggers>
        <fileset>
          <directory>test-data</directory>
          <includes>
            <include>a/*</include>
            <include>b/**</include>
          </includes>
          <excludes>
            <exclude>a/a.txt</exclude>
          </excludes>
        </fileset>
      </test-triggers>
    </project>
  </projects>
</buildcache>
```

The example fileset would match files from `[ROOT-com.test:test-lib]/test-data` and match for example
* all files from directory `a` except for `a.txt`
* all files recursively from directory `b`, like `b/b.txt` and `b/b2/b2.txt`

#### Cache Configuration

Cache directory and size can be configured using a simple XML. The config files are looked up in order and first existing one is loaded

1. `{user.home}/.m2/buildcache.xml`
2. `{maven.home}/conf/buildcache.xml`

Syntax is
```xml
<buildcache>
  <configuration>
    <cache-directory>/root/cache/dir</cache-directory>
    <project-cache-max-size>7M</project-cache-max-size>
    <project-cache-max-entries>100</project-cache-max-entries>
    <project-cache-max-age>P30D</project-cache-max-age>
    <total-cache-max-size>500M</total-cache-max-size>
  </configuration>
</buildcache>
```

all elements are optional. If this configuration file is found then the defaults no longer apply (see table below), except for the `cache-directory`.

| Element                     | Description | Default |
| --------------------------- | ----------- | ------- |
| `cache-directory`           | Directory used for cached data | `{user.home}/.m2/buildcache` |
| `project-cache-max-size`    | Maximum size of a cache directory for a single project in megabytes. For example `7`, `7M` or `7 MB` | |
| `project-cache-max-entries` | Maximum number of cached entries for a single project. Only the `.zip` (containing `.class` files) are counted against this limit. `Integer` | `20` |
| `project-cache-max-age`     | Maximum age for files in a project cache directory. `Integer` (days) or `java.time.Duration` | `90` |
| `total-cache-max-size`      | Total size limit for the whole cache directory in megabytes. For example `500`, `500M` or `500 MB` | |

The project-specific cache limits are enforced on every build for the project being built. Total cache size limit is enforced only when the Maven build is invoked with `-Dbuildcache.fullclean` flag.

#### Command-Line Properties

There are some properties that affect the Buildcache behavior.

| Property               | Description |
| ---------------------- | ----------- |
| `buildcache.disable`   | Maven build is executed as if the Buildcache wasn't there |
| `buildcache.ignore`    | Possible cache hits are ignored and cache entries overwritten by new data |
| `buildcache.fullclean` | Performs cache cleanup for all data in the cache directory |
| `buildcache.profile`   | Outputs profiling information for the build |
| `buildcache.debug`     | Does more verbose logging and outputs `.txt` files to project cache directory showing full hash input |

For example, executing `mvn clean package -Dbuildcache.profile` will print execution time of each plugin goal

```
[INFO] ------------------------------------------------------------------------
[INFO] test-lib
[INFO]   clean ................... org.a...:maven-clean-plugin:3.1.0:clean .............. [  0.102 s]
[INFO]   validate ................ org.a...:maven-enforcer-plugin:3.0.0-M3:enforce ...... [  0.226 s]
[INFO]   process-resources ....... org.a...:maven-resources-plugin:3.0.2:resources ...... [  0.137 s]
[INFO]   compile ................. org.a...:maven-compiler-plugin:3.8.1:compile ......... [  0.080 s] (cache hit)
[INFO]   process-test-resources .. org.a...:maven-resources-plugin:3.0.2:testResources .. [  0.023 s]
[INFO]   test-compile ............ org.a...:maven-compiler-plugin:3.8.1:testCompile ..... [  0.038 s] (cache hit)
[INFO]   test .................... org.a...:maven-surefire-plugin:3.0.0-M5:test ......... [  0.027 s] (cache hit)
[INFO]   package ................. org.a...:maven-jar-plugin:3.0.2:jar .................. [  0.450 s]
```

### Cache Cleanup

The project-specific cache limits are enforced on every build for the project being built. Total cache size limit is enforced only when the Maven build is invoked with `-Dbuildcache.fullclean` flag.

### Debugging

Sometimes it may be useful to see what is being used as the input for the hash calculation. Executing the build with `-Dbuilbcache.debug` will create `.txt` files to the project cache directory that will contain the hash input.

Maven output shows the hash values
```
[INFO] buildcache: caching main classes (ad320a1fb350ca665e1042f147645d3a)
[INFO] buildcache: caching test classes (cc6dc6f89f980a1b9c5ce7bc80c5598b)
```
and you can then find a files `classes-ad320a1fb350ca665e1042f147645d3a.txt` and `test-classes-cc6dc6f89f980a1b9c5ce7bc80c5598b.txt` from the project buildcache directory (for example `{user.home}/.m2/buildcache/com/test/test-lib/`). Test and integration test executions also have similar files.

# Reporting Issues
https://github.com/skauppin/buildcache-maven-extension/issues

