package mr.xe.delid.service.detector;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.api.AbstractPackageLicenseDetector;
import mr.xe.delid.core.ServiceContainer;
import mr.xe.delid.dto.DetectedLicenses;
import mr.xe.delid.dto.PurlDescriptor;
import mr.xe.delid.dto.RepositoryPackageMeta;
import mr.xe.delid.enums.PackageType;
import mr.xe.delid.util.HttpResponseUtils;
import mr.xe.delid.util.detector.MavenPomParser;
import mr.xe.delid.util.detector.ZipStreamPackageExtractor;

@Slf4j
public class MavenPackageLicenseDetector extends AbstractPackageLicenseDetector {

  private static final String CONTENT_TYPE_APP_XML = "application/xml";
  private static final String CONTENT_TYPE_TXT_XML = "text/xml";
  private static final String CONTENT_TYPE_JAR = "application/java-archive";
  private static final String POM_XML = "pom.xml";

  private final MavenPackageRepositoryResponseParser mavenPackageRepositoryResponseParser;

  public MavenPackageLicenseDetector() {
    this.mavenPackageRepositoryResponseParser =
        new MavenPackageRepositoryResponseParser(ServiceContainer.getObjectMapper());
  }

  @Override
  public PackageType getType() {
    return PackageType.MAVEN;
  }

  @Override
  @SneakyThrows
  public Set<DetectedLicenses.LicenseDescriptor> fetchLicenses(PurlDescriptor purlDescriptor) {

    final Set<DetectedLicenses.LicenseDescriptor> licenses = extractLicensesFromDownloadedPomStream(purlDescriptor);
    if (!licenses.isEmpty()) {
      return licenses;
    }

    final HttpResponse<InputStream> jarUrlsResponse =
        ServiceContainer.getPurlRepositoryClient().fetchHttpResponseStream(purlDescriptor.repositoryUrl());
    final List<String> jarUrls =
        mavenPackageRepositoryResponseParser.findDownloadUrlsByContentType(jarUrlsResponse, CONTENT_TYPE_JAR);
    if (jarUrls == null || jarUrls.isEmpty()) {
      return EMPTY_LICENSES;
    }

    final HttpResponse<InputStream> jarResponse =
        ServiceContainer.getPurlRepositoryClient().fetchHttpResponseStream(jarUrls.get(0));
    final RepositoryPackageMeta repositoryPackageMeta =
        ZipStreamPackageExtractor.extractRepositoryPackageMeta(jarResponse, POM_XML);

    return extractFromLicenseOrCopyrightFiles(purlDescriptor, repositoryPackageMeta);

  }

  private Set<DetectedLicenses.LicenseDescriptor> extractLicensesFromDownloadedPomStream(
      PurlDescriptor purlDescriptor) {
    final HttpResponse<InputStream> pomDownloadResponse =
        ServiceContainer.getPurlRepositoryClient().fetchHttpResponseStream(purlDescriptor.repositoryUrl());
    HttpResponseUtils.contentTypeCheck(pomDownloadResponse, Set.of(CONTENT_TYPE_APP_XML, CONTENT_TYPE_TXT_XML));
    try (InputStream inputStream = pomDownloadResponse.body()) {
      return MavenPomParser.extractLicenseNames(inputStream)
          .stream()
          .map(licenseName -> new DetectedLicenses.LicenseDescriptor(
              licenseName,
              POM_XML
          ))
          .collect(Collectors.toSet());
    } catch (Throwable e) {
      log.error("Streaming pom.xml error for artifact {}", purlDescriptor.purl(), e);
    }
    return EMPTY_LICENSES;
  }

}
