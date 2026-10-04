package mr.xe.delid.util.detector;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import mr.xe.delid.core.AbstractUtility;
import mr.xe.delid.dto.RepositoryPackageMeta;
import static mr.xe.delid.config.ApplicationConfig.COPYRIGHT_FILE_NAMES;
import static mr.xe.delid.config.ApplicationConfig.LICENSE_FILE_NAMES;

/**
 * Streaming ZIP extractor.
 */
public class ZipStreamPackageExtractor extends AbstractUtility {

  private static final int BUFFER_SIZE_8KB = 8192;

  /**
   * Extracts the manifest and all license files from a ZIP-stream.
   *
   * @param response HttpResponse<InputStream> with Content-Type: application/zip
   * @return the manifest and all license files
   */
  public static RepositoryPackageMeta extractRepositoryPackageMeta(
      HttpResponse<InputStream> response, String manifestFileEndWithPattern)
      throws IOException {
    RepositoryPackageMeta.MetaFile manifestContent = null;
    final List<RepositoryPackageMeta.MetaFile> licenseContents = new ArrayList<>();
    final List<RepositoryPackageMeta.MetaFile> copyrightContents = new ArrayList<>();

    try (InputStream rawStream = response.body();
         ZipInputStream zis = new ZipInputStream(rawStream, StandardCharsets.UTF_8)) {

      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        final String name = entry.getName().toLowerCase(Locale.ROOT);

        if (name.endsWith(manifestFileEndWithPattern)) {
          if (manifestContent == null) {
            manifestContent = new RepositoryPackageMeta.MetaFile(name, readEntryContent(zis));
          }
        } else if (isLicenseFile(name)) {
          licenseContents.add(new RepositoryPackageMeta.MetaFile(name, readEntryContent(zis)));
        } else if (isCopyrightFile(name)) {
          copyrightContents.add(new RepositoryPackageMeta.MetaFile(name, readEntryContent(zis)));
        }

        zis.closeEntry();
      }
    }

    return new RepositoryPackageMeta(manifestContent, licenseContents, copyrightContents);
  }

  /**
   * Checks whether a file name is a license file.
   */
  private static boolean isLicenseFile(String path) {
    return isFileNameContainsPattern(path, LICENSE_FILE_NAMES);
  }

  /**
   * Checks whether a file name is a potential license file.
   */
  private static boolean isCopyrightFile(String path) {
    return isFileNameContainsPattern(path, COPYRIGHT_FILE_NAMES);
  }

  private static boolean isFileNameContainsPattern(String path, Set<String> pattern) {
    final int lastSlash = path.lastIndexOf('/');
    final String fileName = (lastSlash == -1) ? path : path.substring(lastSlash + 1);
    final int dotIndex = fileName.lastIndexOf('.');
    final String baseName = (dotIndex <= 0) ? fileName : fileName.substring(0, dotIndex);
    return pattern.contains(baseName);
  }

  /**
   * Reads the content of the current ZipInputStream entry to the end. The stream position ends up right after the entry.
   */
  private static String readEntryContent(ZipInputStream zis) throws IOException {
    final StringBuilder sb = new StringBuilder();
    final byte[] buffer = new byte[BUFFER_SIZE_8KB];
    int len;
    while ((len = zis.read(buffer)) != -1) {
      sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
    }
    return sb.toString();
  }
}
