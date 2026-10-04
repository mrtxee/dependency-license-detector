package mr.xe.delid.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import mr.xe.delid.core.AbstractUtility;

@Slf4j
public final class FileUtils extends AbstractUtility {

  private static final Tika MIME_DETECTOR = new Tika();
  private static final int BUFFER_SIZE_8KB = 8192;

  public static String removeExtension(Path path) {
    return FilenameUtils.removeExtension(path.getFileName().toString());
  }

  public static String readFileAsString(Path path) {
    if (path == null) {
      return null;
    }
    try {
      // Paths listed by listClasspathDir point at real files only when the resources are exploded
      // directories. Inside a jar the same paths are classpath entries, so they are read as such.
      if (Files.isRegularFile(path)) {
        return Files.readString(path);
      }
      return readClasspathResourceAsString(path.toString());
    } catch (IOException e) {
      log.error("Error reading " + path);
      return null;
    }
  }

  /** Reads a classpath resource as UTF-8 text, or returns {@code null} when it cannot be read. */
  public static String readClasspathResourceAsString(String resourcePath) {
    try (InputStream resourceStream = FileUtils.class.getClassLoader().getResourceAsStream(resourcePath)) {
      if (resourceStream == null) {
        log.error("Classpath resource not found: " + resourcePath);
        return null;
      }
      return new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("Error reading classpath resource " + resourcePath, e);
      return null;
    }
  }

  public static List<Path> listClasspathDir(String resourcePath) {
    final URL dirUrl = Thread.currentThread()
        .getContextClassLoader()
        .getResource(resourcePath);
    if (dirUrl == null) {
      throw new IllegalStateException("Resource not found in classpath: " + resourcePath);
    }

    if (!"jar".equals(dirUrl.getProtocol())) {
      try (Stream<Path> stream = Files.list(Paths.get(dirUrl.toURI()))) {
        return stream
            .filter(Files::isRegularFile)
            .collect(Collectors.toList());
      } catch (Throwable e) {
        log.error("Error listing files in classpath directory: " + resourcePath, e);
      }
    }

    try {
      final String jarPath = dirUrl.getPath().substring(5, dirUrl.getPath().indexOf("!"));
      try (var jarFile = new java.util.jar.JarFile(jarPath)) {
        return jarFile.stream()
            .filter(entry -> entry.getName().startsWith(resourcePath))
            .map(jarEntry -> Path.of(jarEntry.getName()))
            .toList();
      }
    } catch (IOException e) {
      throw new RuntimeException("URI error: " + resourcePath, e);
    }
  }

  /**
   * Removes files and directories under the given path
   *
   * @param path path
   */
  public static void cleanUp(Path path) {
    try {
      if (path == null) {
        return;
      }
      if (Files.isDirectory(path)) {
        FileUtils.deleteDirectory(path);
      } else {
        Files.deleteIfExists(path);
      }
    } catch (IOException e) {
      log.error("Cleanup error {}", e.getMessage());
    }

  }

  /**
   * Determines the MIME type of a file
   *
   * @param path path
   */
  public static String detectType(Path path) throws IOException {
    return MIME_DETECTOR.detect(path);
  }

  /**
   * Finds all files that may contain licenses, by name (case-insensitive).
   *
   * @param packageDir root directory to search in
   * @return list of the found file paths
   */
  public static List<Path> findLicenseFiles(Path packageDir, Set<String> licenseFileNames) throws IOException {
    if (!Files.exists(packageDir)) {
      return List.of();
    }
    final List<Path> result = new ArrayList<>();
    Files.walkFileTree(packageDir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        final String fileName = file.getFileName().toString();
        final String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (matchesLicensePattern(lowerName, licenseFileNames)) {
          result.add(file);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) {
        return FileVisitResult.CONTINUE;
      }
    });

    return result;
  }

  /**
   * Recursively unpacks archives (zip/gzip/tar and their combinations).
   * Deletes all intermediate files (including the input archive).
   * Returns the path to the directory with the resulting content.
   */
  public static void unpackFile(Path archivedFile, Path outputDir) throws IOException {
    prepareOutputDirectory(outputDir);
    final Set<Path> filesToDelete = new HashSet<>();
    filesToDelete.add(archivedFile);
    unpackRecursive(archivedFile, outputDir, filesToDelete);
    for (Path p : filesToDelete) {
      if (Files.exists(p)) {
        if (Files.isDirectory(p)) {
          deleteDirectory(p);
        } else {
          Files.delete(p);
        }
      }
    }

  }

  /**
   * Downloads the body of an HTTP response (application/octet-stream) and saves it to the given temporary file.
   *
   * @param response   HTTP response with an InputStream body
   * @param targetPath path to save the file to
   * @throws IOException if an I/O error occurred
   */
  public static void downloadToPath(HttpResponse<InputStream> response, Path targetPath) throws IOException {

    prepareOutputDirectory(targetPath);

    try (InputStream input = response.body()) {
      Files.copy(input, targetPath);
    } catch (IOException e) {
      Files.deleteIfExists(targetPath);
      throw e;
    }
  }

  public static List<Path> findFilesInDirectoryTree(Path startPath, String fileName) throws IOException {
    if (!Files.isDirectory(startPath)) {
      throw new IOException("Illegal base directory: " + startPath);
    }
    final List<Path> foundFiles = new ArrayList<>();
    Files.walkFileTree(startPath, new SimpleFileVisitor<>() {
      @Override
      public @Nonnull FileVisitResult visitFile(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) {
        if (file.getFileName().toString().equals(fileName)) {
          foundFiles.add(file);
        }
        return FileVisitResult.CONTINUE;
      }
    });
    return foundFiles;
  }

  public static Path findFirstFileInDirectoryTree(Path startPath, String fileName) throws IOException {
    if (!Files.isDirectory(startPath)) {
      throw new IOException("Illegal base directory: " + startPath);
    }
    final Path[] result = new Path[1];
    Files.walkFileTree(startPath, new SimpleFileVisitor<>() {
          @Override
          public @Nonnull FileVisitResult visitFile(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) {
            if (file.getFileName().toString().equalsIgnoreCase(fileName)) {
              result[0] = file;
              return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
          }
        }
    );
    return result[0];
  }

  /**
   * Writes text to a file at the given path.
   * Creates parent directories if they do not exist.
   *
   * @param path absolute or relative path to the file
   * @param text text to write
   * @throws IOException if an I/O error occurred
   */
  public static void writeFile(Path path, String text) throws IOException {
    final Path parent = path.getParent();
    if (parent != null && !Files.exists(parent)) {
      Files.createDirectories(parent);
    }
    Files.writeString(path, text, StandardCharsets.UTF_8);
  }

  /**
   * Finds all files in the baseDir directory matching the globExpr pattern.
   * Returns a list of absolute paths to the found files.
   *
   * @param baseDir  absolute or relative path to the search directory (String)
   * @param globExpr glob pattern, for example "**&#47;info/required-software.json"
   * @return List of absolute paths (String) to the found files
   */
  public static List<Path> findFilesByGlob(String baseDir, String globExpr) throws IOException {
    final Path startPath = Paths.get(baseDir).toAbsolutePath();

    if (!Files.isDirectory(startPath)) {
      throw new IllegalArgumentException("Указанный путь не является директорией: " + startPath);
    }

    String normalizedGlob = globExpr;
    if (normalizedGlob.startsWith("/")) {
      normalizedGlob = normalizedGlob.substring(1);
    }

    final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedGlob);

    final List<Path> result = new ArrayList<>();

    Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
      @Override
      public @Nonnull FileVisitResult visitFile(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) {
        if (matcher.matches(file)) {
          result.add(file);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public @Nonnull FileVisitResult visitFileFailed(@Nonnull Path file, @Nonnull IOException exc) {
        return FileVisitResult.CONTINUE;
      }
    });

    return result;
  }

  public static void deleteDirectory(Path directory) throws IOException {
    if (Files.exists(directory)) {
      Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          if (exc == null) {
            Files.delete(dir);
          }
          return FileVisitResult.CONTINUE;
        }
      });
    }
  }

  public static void unpackZip(Path zipPath, Path destDir, Set<Path> filesToDelete) throws IOException {
    try (var fis = Files.newInputStream(zipPath); var zis = new java.util.zip.ZipInputStream(fis)) {

      java.util.zip.ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        final Path target = destDir.resolve(entry.getName()).normalize();
        if (!target.toAbsolutePath().startsWith(destDir.toAbsolutePath().normalize())) {
          zis.closeEntry();
          continue;
        }

        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          try (var out = Files.newOutputStream(target, StandardOpenOption.CREATE,
              StandardOpenOption.TRUNCATE_EXISTING)) {
            final byte[] buffer = new byte[BUFFER_SIZE_8KB];
            int len;
            while ((len = zis.read(buffer)) != -1) {
              out.write(buffer, 0, len);
            }
          }
        }
        zis.closeEntry();
      }
    }
    filesToDelete.add(zipPath);
  }

  public static void unpackTar(Path tarPath, Path destDir, Set<Path> filesToDelete) throws IOException {
    try (var fis = Files.newInputStream(tarPath);
         var tis = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(fis)) {

      org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
      while ((entry = tis.getNextTarEntry()) != null) {
        final Path target = destDir.resolve(entry.getName()).normalize();
        if (!target.toAbsolutePath().startsWith(destDir.toAbsolutePath().normalize())) {
          continue;
        }

        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          try (var out = Files.newOutputStream(target, StandardOpenOption.CREATE,
              StandardOpenOption.TRUNCATE_EXISTING)) {
            final byte[] buffer = new byte[BUFFER_SIZE_8KB];
            final long size = entry.getSize();
            long totalRead = 0;
            int len;
            while (totalRead < size && (len = tis.read(buffer)) != -1) {
              final long toWrite = Math.min(len, size - totalRead);
              out.write(buffer, 0, (int) toWrite);
              totalRead += toWrite;
            }
          }
        }
      }
    }
    filesToDelete.add(tarPath);
  }

  public static void unpackGzipContainingTar(Path gzipPath, Path destDir, Set<Path> filesToDelete) throws IOException {
    try (var fis = Files.newInputStream(gzipPath); var gis = new GzipCompressorInputStream(fis);
         var tis = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gis)) {

      org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
      while ((entry = tis.getNextTarEntry()) != null) {
        final Path target = destDir.resolve(entry.getName()).normalize();
        if (!target.toAbsolutePath().startsWith(destDir.toAbsolutePath().normalize())) {
          continue;
        }

        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          try (var out = Files.newOutputStream(target, StandardOpenOption.CREATE,
              StandardOpenOption.TRUNCATE_EXISTING)) {
            final byte[] buffer = new byte[BUFFER_SIZE_8KB];
            final long size = entry.getSize();
            long totalRead = 0;
            int len;
            while (totalRead < size && (len = tis.read(buffer)) != -1) {
              final long toWrite = Math.min(len, size - totalRead);
              out.write(buffer, 0, (int) toWrite);
              totalRead += toWrite;
            }
          }
        }
      }
    }
    filesToDelete.add(gzipPath);
  }

  /**
   * Checks whether a file name matches a license pattern.
   * Recognised variants: LICENSE, LICENSE.txt, LICENSE.md, license.rst, etc.
   */
  private static boolean matchesLicensePattern(String lowerName, Set<String> licenseFileNames) {
    if (licenseFileNames.contains(lowerName)) {
      return true;
    }
    final int dotIndex = lowerName.lastIndexOf('.');
    if (dotIndex > 0) {
      final String baseName = lowerName.substring(0, dotIndex);
      if (licenseFileNames.contains(baseName)) {
        return true;
      }
    }

    return false;
  }

  private static void prepareOutputDirectory(Path outputDir) throws IOException {
    if (Files.exists(outputDir)) {
      if (Files.isDirectory(outputDir)) {
        deleteDirectory(outputDir);
      } else {
        Files.delete(outputDir);
      }
    }
    if (outputDir.getParent() != null) {
      Files.createDirectories(outputDir.getParent());
    }
  }

  private static byte[] readHeader(Path path, int length) throws IOException {
    try (var fc = FileChannel.open(path)) {
      final ByteBuffer buffer = java.nio.ByteBuffer.allocate(length);
      fc.read(buffer);
      buffer.flip();
      final byte[] result = new byte[buffer.remaining()];
      buffer.get(result);
      return result;
    }
  }

  private static void unpackRecursive(Path source, Path destDir, Set<Path> filesToDelete) throws IOException {
    final String type = detectType(source);
    switch (type) {
      case "application/zip":
        unpackZip(source, destDir, filesToDelete);
        break;
      case "application/gzip":
        unpackGzipContainingTar(source, destDir, filesToDelete);
        break;
      case "application/tar":
        unpackTar(source, destDir, filesToDelete);
        break;
      default:
        log.warn("Unexpected archive type: " + type);
        break;
    }
  }

}
