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
import mr.xe.delid.util.detector.ZipStreamPackageExtractor;

@Slf4j
public class GoPackageLicenseDetector extends AbstractPackageLicenseDetector {

  private static final String CONTENT_TYPE_ZIP = "application/zip";
  private static final String MANIFEST_FILE_NAME = "go.mod";

  @Override
  public PackageType getType() {
    return PackageType.GO;
  }

  @Override
  public Set<DetectedLicenses.LicenseDescriptor> fetchLicenses(PurlDescriptor purlDescriptor) {
    final HttpResponse<InputStream> response =
        ServiceContainer.getPurlRepositoryClient().fetchHttpResponseStream(purlDescriptor.repositoryUrl());
    HttpResponseUtils.contentTypeCheck(response, CONTENT_TYPE_ZIP);
    try {
      final RepositoryPackageMeta repositoryPackageMeta =
          ZipStreamPackageExtractor.extractRepositoryPackageMeta(response, "/".concat(MANIFEST_FILE_NAME));
      return extractFromLicenseOrCopyrightFiles(purlDescriptor, repositoryPackageMeta);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
