package mr.xe.delid.app;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.core.ServiceContainer;
import mr.xe.delid.dto.DetectedLicenses;
import static java.lang.String.format;

/**
 * Runs {@link PackageLicenseDetectionScenario#detectLicenses} for every package declared in
 * {@code src/test/resources/artifactory/manifest.json} and reports the outcome in the log.
 *
 * <p>A manual counterpart of {@link PackageLicenseDetectionScenarioTest}: the test asserts a fixed expectation per
 * package, this runner asserts nothing and only reports what was detected, which makes it a convenient way to
 * eyeball the detectors over the whole fixture set at once, for example while tuning a detector or a license
 * signature.
 *
 * <p>Deliberately not a JUnit test: the class name does not match the surefire include patterns
 * ({@code Test*}, {@code *Test}, {@code *Tests}, {@code *TestCase}) and it is excluded explicitly in
 * {@code pom.xml}, so {@code mvn test} never runs it.
 *
 * <p>The packages are served by {@link ArtifactoryFixtureServer} over a loopback HTTP port, so the runner needs no
 * network access and no real artifactory. It does need the environment variables read by
 * {@code ApplicationConfig}: {@code WORKDIR_BASE_PATH} and {@code DETECTED_LICENSES_FILE}.
 *
 * <p>Launch it from the IDE, or from the command line:
 * <pre>
 * WORKDIR_BASE_PATH=./target/runner-workdir DETECTED_LICENSES_FILE=detected-licenses.json \
 *   mvn test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=mr.xe.delid.app.ManifestLicenseDetectionRunner
 * </pre>
 *
 * <p>A detection failure of a single package does not stop the run: the error is logged and the remaining packages
 * are still processed, so one broken fixture does not hide the state of the rest.
 */
@Slf4j
public final class ManifestLicenseDetectionRunner {

  private static final String SUMMARY_MESSAGE = "%d of %d packages detected at least one license";
  private static final String DETECTION_FAILED_MESSAGE = "License detection failed for purl %s: %s";

  private ManifestLicenseDetectionRunner() {
  }

  public static void main(String[] args) {
    ServiceContainer.init();

    try (ArtifactoryFixtureServer artifactory = ArtifactoryFixtureServer.start()) {
      final List<ArtifactoryFixtureServer.ManifestPackage> packages = artifactory.packages();
      final DetectionSummary summary = detectLicensesOfAll(packages);
      log.info(SUMMARY_MESSAGE.formatted(summary.packagesWithLicenses(), packages.size()));
      if (summary.failures() > 0) {
        log.warn("{} of {} packages failed to be processed", summary.failures(), packages.size());
      }
    }
  }

  /** Detects the licenses of every package in order and reports the PURL and the licenses of each one. */
  private static DetectionSummary detectLicensesOfAll(List<ArtifactoryFixtureServer.ManifestPackage> packages) {
    int packagesWithLicenses = 0;
    int failures = 0;

    for (ArtifactoryFixtureServer.ManifestPackage pkg : packages) {
      try {
        final Set<DetectedLicenses.LicenseDescriptor> licenses =
            PackageLicenseDetectionScenario.detectLicenses(pkg.purl(), pkg.repositoryUrl());
        final Set<String> licenseIds = licenseIdsOf(licenses);
        log.info("Purl {}: licenses detected: {} (registry reports: {})", pkg.purl(), licenseIds, pkg.registryLicense());
        if (!licenseIds.isEmpty()) {
          packagesWithLicenses++;
        }
      } catch (Exception e) {
        failures++;
        log.error(DETECTION_FAILED_MESSAGE, pkg.purl(), e.toString());
        log.debug("License detection failure of purl {}", pkg.purl(), e);
      }
    }

    return new DetectionSummary(packagesWithLicenses, failures);
  }

  private static Set<String> licenseIdsOf(Set<DetectedLicenses.LicenseDescriptor> licenses) {
    return licenses.stream()
        .map(DetectedLicenses.LicenseDescriptor::getLicenseId)
        .collect(Collectors.toSet());
  }

  /** Outcome of a run over the whole manifest. */
  private record DetectionSummary(int packagesWithLicenses, int failures) {
  }

}
