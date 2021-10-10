package io.github.skauppin.maven.buildcache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.maven.model.FileSet;
import org.codehaus.plexus.util.DirectoryScanner;
import com.google.common.io.Files;

public class FileUtil {

  public FileUtil() {}

  public Pair<List<FileSet>> mergeSourceRootsToFileSets(List<String> sourceRoots,
      List<FileSet> filesets) {

    Set<String> uniqueSourceRoots = sourceRoots.stream().collect(Collectors.toSet());

    List<FileSet> sourceRootFileSets = uniqueSourceRoots.stream().map(r -> {
      FileSet set = new FileSet();
      set.setDirectory(r);
      set.addInclude("**");
      return set;
    }).collect(Collectors.toList());

    return mergeFileSets(sourceRootFileSets, filesets);
  }

  public Pair<List<FileSet>> mergeFileSets(List<? extends FileSet> primary,
      List<FileSet> secondary) {

    List<FileSet> primaryCopy = new ArrayList<>(primary);
    List<FileSet> additional = new ArrayList<>();

    for (FileSet set : secondary) {
      Optional<FileSet> optMatch = getMatchingFileSet(primaryCopy, set);

      if (optMatch.isPresent()) {
        FileSet match = optMatch.get();
        FileSet mergedCopy = mergeFileSets(match, set);
        replaceFileSet(primaryCopy, match, mergedCopy);

      } else {
        Optional<FileSet> optMatchAdditional = getMatchingFileSet(additional, set);
        if (optMatchAdditional.isPresent()) {
          FileSet match = optMatchAdditional.get();
          FileSet mergedCopy = mergeFileSets(match, set);
          replaceFileSet(additional, match, mergedCopy);

        } else {
          additional.add(set);
        }
      }
    }
    return new Pair<>(primaryCopy, additional);
  }

  private void replaceFileSet(List<FileSet> list, FileSet remove, FileSet add) {
    int indexOf = list.indexOf(remove);
    list.remove(indexOf);
    list.add(indexOf, add);
  }

  private Optional<FileSet> getMatchingFileSet(List<FileSet> list, FileSet set) {
    return list.stream()
        .filter(m -> Paths.get(m.getDirectory()).equals(Paths.get(set.getDirectory()))).findFirst();
  }

  private FileSet mergeFileSets(FileSet set1, FileSet set2) {
    FileSet copy = copyFileSet(set1);
    copy.getIncludes().addAll(set2.getIncludes());
    copy.getExcludes().addAll(set2.getExcludes());
    return copy;
  }

  public FileSet copyFileSet(FileSet set) {
    FileSet copy = new FileSet();
    copy.setDirectory(set.getDirectory());
    copy.getIncludes().addAll(set.getIncludes());
    copy.getExcludes().addAll(set.getExcludes());
    return copy;
  }

  public String[] scanFiles(FileSet set) {
    File dir = new File(set.getDirectory());
    if (!dir.exists()) {
      return new String[0];
    }
    DirectoryScanner s = new DirectoryScanner();
    s.setBasedir(set.getDirectory());
    if (!set.getIncludes().isEmpty()) {
      s.setIncludes(set.getIncludes().toArray(new String[0]));
    }
    if (!set.getExcludes().isEmpty()) {
      s.setExcludes(set.getExcludes().toArray(new String[0]));
    }
    s.scan();
    String[] filenames = s.getIncludedFiles();
    Arrays.sort(filenames, String.CASE_INSENSITIVE_ORDER);
    return filenames;
  }

  public String[] scanFilesFromDirectory(Path dir) {
    return scanFilesFromDirectory(dir, null);
  }

  public String[] scanClassFilesFromDirectory(Path dir) {
    return scanFilesFromDirectory(dir, "**/*.class");
  }

  public String[] scanFilesFromDirectory(Path dir, String include) {
    FileSet set = new FileSet();
    set.setDirectory(dir.toFile().getAbsolutePath());
    if (include != null) {
      set.addInclude(include);
    }
    return scanFiles(set);
  }

  public boolean createZipFile(Path zipFile, Path archiveBaseDir, String[] archiveFiles)
      throws FileNotFoundException, IOException {
    zipFile.getParent().toFile().mkdirs();
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
      for (String file : archiveFiles) {
        ZipEntry entry = new ZipEntry(file);
        zip.putNextEntry(entry);
        IOUtils.copy(new FileInputStream(archiveBaseDir.resolve(file).toFile()), zip);
        zip.closeEntry();
      }
    }
    return true;
  }

  public boolean unzip(Path zipFile, Path targetDir) throws FileNotFoundException, IOException {
    try (ZipFile zip = new ZipFile(zipFile.toFile())) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = entries.nextElement();
        Path targetFile = targetDir.resolve(zipEntry.getName());
        targetFile.getParent().toFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(targetFile.toFile());
            InputStream in = zip.getInputStream(zipEntry)) {
          IOUtils.copy(in, out);
        }
      }
    }
    return true;
  }

  public void writeFile(Path file, String content) throws IOException {
    if (content == null) {
      return;
    }
    Files.asCharSink(file.toFile(), Charset.forName("UTF-8")).write(content);
  }

  public void deleteFile(Path file) {
    deleteFile(file.toFile());
  }

  public void deleteFile(File file) {
    if (!file.exists()) {
      return;
    }
    file.delete();
  }

  public boolean fileExists(Path path) {
    return path.toFile().exists();
  }

  public boolean mkdirs(Path path) {
    return path.toFile().mkdirs();
  }

  public File[] listFiles(File directory) {
    return directory.listFiles();
  }
}