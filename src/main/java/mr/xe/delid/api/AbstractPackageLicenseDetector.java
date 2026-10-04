package mr.xe.delid.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.core.ServiceContainer;
import mr.xe.delid.dto.DetectedLicenses;
import mr.xe.delid.dto.PurlDescriptor;
import mr.xe.delid.dto.RepositoryPackageMeta;
import mr.xe.delid.enums.PackageType;
import mr.xe.delid.util.FileUtils;
import static mr.xe.delid.config.ApplicationConfig.COPYRIGHT_FILE_NAMES;
import static mr.xe.delid.config.ApplicationConfig.LICENSE_FILE_NAMES;
import static mr.xe.delid.util.FileUtils.readFileAsString;

/**
 * Contract for a service that accesses a specific package repository
 */
@Slf4j
public abstract class AbstractPackageLicenseDetector implements PackageLicenseDetector {
  protected static final Set<DetectedLicenses.LicenseDescriptor> EMPTY_LICENSES = Set.of();
  protected static final String ARCHIVE_FILE_POSTFIX = ".archive";

  /**
   * Returns the repository type
   */
  public abstract PackageType getType();

  public Set<DetectedLicenses.LicenseDescriptor> fetchLicenses(PurlDescriptor purlDescriptor) {
    log.error("Package type {} is not supported. Purl {} is skipped", getType(), purlDescriptor.purl());
    return EMPTY_LICENSES;
  }

  /**
   * Extracts licenses from the package license files
   */
  protected Set<DetectedLicenses.LicenseDescriptor> extractFromLicenseFiles(
      PurlDescriptor purlDescriptor,
      List<RepositoryPackageMeta.MetaFile> metaFiles) {
    if (metaFiles.isEmpty()) {
      return EMPTY_LICENSES;
    }
    return metaFiles.stream()
        .map(metaFile -> {
              final String licenseId = ServiceContainer.getLicenseSignatureMatcher().findMatchLicenseId(
                  purlDescriptor.purl(),
                  metaFile.name(),
                  metaFile.text());
              return new DetectedLicenses.LicenseDescriptor(
                  licenseId,
                  metaFile.name()
              );
            }
        )
        .collect(Collectors.toSet());
  }

  /**
   * Extracts licenses from the package metadata files.
   * Copyright files are analysed only if no license files are found.
   */
  protected Set<DetectedLicenses.LicenseDescriptor> extractFromLicenseOrCopyrightFiles(
      PurlDescriptor purlDescriptor,
      RepositoryPackageMeta metaFiles) {
    final Set<DetectedLicenses.LicenseDescriptor> detectedLicenses =
        extractFromLicenseFiles(purlDescriptor, metaFiles.licenses());
    if (!detectedLicenses.isEmpty()) {
      return detectedLicenses;
    }
    return extractFromLicenseFiles(purlDescriptor, metaFiles.copyrights());
  }

  protected Set<DetectedLicenses.LicenseDescriptor> extractFromLicenseOrCopyrightFiles(
      PurlDescriptor purlDescriptor,
      Path packageDir) throws IOException {
    final Set<DetectedLicenses.LicenseDescriptor> licenses =
        extractFromLicenseFilePaths(purlDescriptor, FileUtils.findLicenseFiles(packageDir, LICENSE_FILE_NAMES));
    if (!licenses.isEmpty()) {
      return licenses;
    }
    return extractFromLicenseFilePaths(purlDescriptor, FileUtils.findLicenseFiles(packageDir, COPYRIGHT_FILE_NAMES));
  }

  protected Set<DetectedLicenses.LicenseDescriptor> extractFromLicenseFilePaths(PurlDescriptor purlDescriptor,
                                                                                List<Path> licenseFiles) {
    if (licenseFiles.isEmpty()) {
      return EMPTY_LICENSES;
    }
    return licenseFiles.stream()
        .map(path -> {
              final String licenseId = ServiceContainer.getLicenseSignatureMatcher().findMatchLicenseId(
                  purlDescriptor.purl(),
                  path.toString(),
                  readFileAsString(path));
              return new DetectedLicenses.LicenseDescriptor(
                  licenseId,
                  path.toString()
              );
            }
        )
        .collect(Collectors.toSet());
  }

}
