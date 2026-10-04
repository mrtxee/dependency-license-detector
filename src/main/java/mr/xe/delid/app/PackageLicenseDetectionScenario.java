package mr.xe.delid.app;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.api.AbstractPackageLicenseDetector;
import mr.xe.delid.core.ServiceContainer;
import mr.xe.delid.dto.DetectedLicenses;
import mr.xe.delid.dto.PurlDescriptor;
import mr.xe.delid.service.PurlStringParser;
import static java.lang.String.format;

/**
 * License detection scenario for a single package identified by its PURL.
 *
 * <p>Expects {@link ServiceContainer} to be already initialised by the entry point {@link App}.
 * It does not depend on how the initialisation was done, so it can be called repeatedly and from tests.
 *
 * <p>Detection failures are propagated to the caller, so that an empty result always means
 * "the package declares no licenses" and never "the detection has failed".
 *
 * <p>Package types without a detector implementation ({@link mr.xe.delid.enums.PackageType#DOCKER},
 * {@link mr.xe.delid.enums.PackageType#RAW}) return an empty set and log an error, see
 * {@link AbstractPackageLicenseDetector#fetchLicenses(PurlDescriptor)}.
 */
@Slf4j
public final class PackageLicenseDetectionScenario {

  private PackageLicenseDetectionScenario() {
  }

  /**
   * Detects the licenses of a single package.
   *
   * @param purl          package URL, for example {@code pkg:maven/com.clickhouse/clickhouse-jdbc@0.7.0}
   * @param repositoryUrl repository base URL to fetch the package from; null or blank means the
   *                      repository configured at application startup
   * @return detected licenses, empty when the package declares none
   * @throws IllegalArgumentException if the PURL is malformed, or either argument is null
   * @throws IllegalStateException    if the package is unreachable, or no detector is registered for
   *                                  its type
   */
  public static Set<DetectedLicenses.LicenseDescriptor> detectLicenses(String purl, String repositoryUrl) {
    final PurlDescriptor purlDescriptor = PurlStringParser.parse(purl, repositoryUrl);
    final AbstractPackageLicenseDetector detector =
        ServiceContainer.getPackageLicenseDetectorFactory().get(purlDescriptor.type());
    if (detector == null) {
      throw new IllegalStateException(
          format("No license detector is registered for package type %s (purl %s)", purlDescriptor.type(), purl));
    }
    final Set<DetectedLicenses.LicenseDescriptor> licenses = detector.fetchLicenses(purlDescriptor);
    log.info("Package {}: licenses detected: {}", purlDescriptor.purl(),
        licenses.stream()
            .map(DetectedLicenses.LicenseDescriptor::getLicenseId)
            .collect(Collectors.toSet()));
    return licenses;
  }

}
