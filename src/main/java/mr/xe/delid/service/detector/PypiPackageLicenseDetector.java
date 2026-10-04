package mr.xe.delid.service.detector;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.api.AbstractPackageLicenseDetector;
import mr.xe.delid.core.ServiceContainer;
import mr.xe.delid.dto.DetectedLicenses;
import mr.xe.delid.dto.PurlDescriptor;
import mr.xe.delid.dto.RepositoryPackageMeta;
import mr.xe.delid.enums.PackageType;
import mr.xe.delid.util.HttpResponseUtils;
import mr.xe.delid.util.detector.ZipStreamPackageExtractor;

@Slf4j
public class PypiPackageLicenseDetector extends AbstractPackageLicenseDetector {

  private static final String CONTENT_TYPE_ZIP = "application/zip";
  private static final String MANIFEST_FILE_END_WITH_PATTERN = "/metadata";

  @Override
  public PackageType getType() {
    return PackageType.PYPI;
  }

  @Override
  public Set<DetectedLicenses.LicenseDescriptor> fetchLicenses(PurlDescriptor purlDescriptor) {
    final HttpResponse<InputStream> response =
        ServiceContainer.getPurlRepositoryClient().fetchHttpResponseStream(purlDescriptor.repositoryUrl());
    HttpResponseUtils.contentTypeCheck(response, CONTENT_TYPE_ZIP);
    try {
      final RepositoryPackageMeta repositoryPackageMeta =
          ZipStreamPackageExtractor.extractRepositoryPackageMeta(response, MANIFEST_FILE_END_WITH_PATTERN);
      final Set<DetectedLicenses.LicenseDescriptor> licenses = extractFromManifest(repositoryPackageMeta.manifest());
      if (!licenses.isEmpty()) {
        return licenses;
      }
      return extractFromLicenseOrCopyrightFiles(purlDescriptor, repositoryPackageMeta);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Set<DetectedLicenses.LicenseDescriptor> extractFromManifest(RepositoryPackageMeta.MetaFile manifest) {
    if (manifest == null) {
      return EMPTY_LICENSES;
    }
    return extractLicenseFromMetadata(manifest.text()).map(licenseName ->
            Set.of(new DetectedLicenses.LicenseDescriptor(
                licenseName, manifest.name()
            ))
        )
        .orElse(EMPTY_LICENSES);
  }

  /**
   * Extracts the value of the License field from METADATA text (PEP 643).
   *
   * @param metadataText full text of the METADATA file
   * @return Optional holding the License field value (trimmed), or empty if the field is not found
   */
  private Optional<String> extractLicenseFromMetadata(String metadataText) {
    if (metadataText == null || metadataText.isBlank()) {
      return Optional.empty();
    }

    final String targetPrefix = "License: ";
    for (String line : metadataText.split("\\R")) { // \\R — any line break sequence (LF, CRLF, etc.)
      if (line.startsWith(targetPrefix)) {
        final String value = line.substring(targetPrefix.length()).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
      }
    }

    return Optional.empty();
  }

}
