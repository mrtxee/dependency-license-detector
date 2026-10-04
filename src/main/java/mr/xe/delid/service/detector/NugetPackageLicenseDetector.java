package mr.xe.delid.service.detector;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.api.AbstractPackageLicenseDetector;
import mr.xe.delid.core.ServiceContainer;
import mr.xe.delid.dto.DetectedLicenses;
import mr.xe.delid.dto.PurlDescriptor;
import mr.xe.delid.dto.RepositoryPackageMeta;
import mr.xe.delid.enums.PackageType;
import mr.xe.delid.util.HttpResponseUtils;
import mr.xe.delid.util.detector.NugetNuspecParser;
import mr.xe.delid.util.detector.ZipStreamPackageExtractor;

@Slf4j
public class NugetPackageLicenseDetector extends AbstractPackageLicenseDetector {

  private static final String CONTENT_TYPE_ZIP = "application/zip";
  private static final String CONTENT_TYPE_OCTET = "application/octet-stream";
  private static final String MANIFEST_FILE_END_WITH_PATTERN = ".nuspec";

  @Override
  public PackageType getType() {
    return PackageType.NUGET;
  }

  @Override
  public Set<DetectedLicenses.LicenseDescriptor> fetchLicenses(PurlDescriptor purlDescriptor) {
    final HttpResponse<InputStream> response =
        ServiceContainer.getPurlRepositoryClient().fetchHttpResponseStream(purlDescriptor.repositoryUrl());
    HttpResponseUtils.contentTypeCheck(response, Set.of(CONTENT_TYPE_OCTET, CONTENT_TYPE_ZIP));
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
    return NugetNuspecParser.extractLicense(manifest.text())
        .map(licenseId -> Set.of(
            new DetectedLicenses.LicenseDescriptor(
                licenseId,
                manifest.name())))
        .orElse(EMPTY_LICENSES);
  }

}
