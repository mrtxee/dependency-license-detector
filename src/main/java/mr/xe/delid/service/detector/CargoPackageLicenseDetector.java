package mr.xe.delid.service.detector;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.api.AbstractPackageLicenseDetector;
import mr.xe.delid.core.ServiceContainer;
import mr.xe.delid.dto.DetectedLicenses;
import mr.xe.delid.dto.PurlDescriptor;
import mr.xe.delid.enums.PackageType;
import mr.xe.delid.util.FileUtils;
import mr.xe.delid.util.HttpResponseUtils;
import static java.lang.String.format;
import static mr.xe.delid.config.ApplicationConfig.TEMP_DIR;
import static mr.xe.delid.config.ApplicationConfig.WORKDIR_BASE_PATH;
import static mr.xe.delid.util.FileUtils.findFirstFileInDirectoryTree;

@Slf4j
public class CargoPackageLicenseDetector extends AbstractPackageLicenseDetector {

  private static final String CONTENT_TYPE_OCTET = "application/octet-stream";
  private static final String CONTENT_TYPE_GZIP = "application/gzip";
  private static final String MANIFEST_FILE_NAME = "cargo.toml";
  private static final Pattern MANIFEST_LICENSE_STRING_PATTERN = Pattern.compile(
      "^\\s*license\\s*=\\s*[\"']([^\"']+)[\"']",
      Pattern.MULTILINE
  );

  @Override
  public PackageType getType() {
    return PackageType.CARGO;
  }

  @Override
  public Set<DetectedLicenses.LicenseDescriptor> fetchLicenses(PurlDescriptor purlDescriptor) {

    final HttpResponse<InputStream> response =
        ServiceContainer.getPurlRepositoryClient().fetchHttpResponseStream(purlDescriptor.repositoryUrl());
    HttpResponseUtils.contentTypeCheck(response, Set.of(CONTENT_TYPE_OCTET, CONTENT_TYPE_GZIP));

    Path downloadedFilePath = null;
    Path extractedFilesDir = null;
    try {
      downloadedFilePath =
          Paths.get(WORKDIR_BASE_PATH).resolve(TEMP_DIR).resolve(purlDescriptor.getFileName() + ARCHIVE_FILE_POSTFIX);
      extractedFilesDir = downloadedFilePath.getParent().resolve(purlDescriptor.getFileName());

      FileUtils.downloadToPath(response, downloadedFilePath);
      FileUtils.unpackFile(downloadedFilePath, extractedFilesDir);

      final Path manifestFilePath = findFirstFileInDirectoryTree(extractedFilesDir, MANIFEST_FILE_NAME);
      final Set<DetectedLicenses.LicenseDescriptor> licenses = extractFromManifest(manifestFilePath);
      if (!licenses.isEmpty()) {
        return licenses;
      }
      return extractFromLicenseOrCopyrightFiles(purlDescriptor, extractedFilesDir);
    } catch (Exception e) {
      throw new RuntimeException(format("Package %s license detection error", purlDescriptor), e);
    } finally {
      FileUtils.cleanUp(downloadedFilePath);
      FileUtils.cleanUp(extractedFilesDir);
    }
  }

  /**
   * Extracts the value of the license field from a Cargo.toml file using a regex.
   * Reads the file into a string. Safe for Cargo.toml, since the size is usually below 100 KB.
   */
  private Optional<String> extractLicenseFromManifestFile(Path cargoTomlPath) {
    if (cargoTomlPath == null || !Files.exists(cargoTomlPath) || !Files.isRegularFile(cargoTomlPath)) {
      return Optional.empty();
    }

    try {
      final String content = Files.readString(cargoTomlPath, StandardCharsets.UTF_8);
      final Matcher matcher = MANIFEST_LICENSE_STRING_PATTERN.matcher(content);
      if (matcher.find()) {
        return Optional.of(matcher.group(1));
      }
      return Optional.empty();
    } catch (Exception e) {
      System.err.println("Failed to read Cargo.toml at " + cargoTomlPath + ": " + e.getMessage());
      return Optional.empty();
    }
  }

  private Set<DetectedLicenses.LicenseDescriptor> extractFromManifest(Path manifestPath) {
    if (manifestPath == null || Files.notExists(manifestPath) || Files.isDirectory(manifestPath)) {
      return EMPTY_LICENSES;
    }

    return extractLicenseFromManifestFile(manifestPath)
        .map(licenseName -> new DetectedLicenses.LicenseDescriptor(
            licenseName, manifestPath.toString()
        ))
        .map(Set::of)
        .orElse(EMPTY_LICENSES);
  }

}
