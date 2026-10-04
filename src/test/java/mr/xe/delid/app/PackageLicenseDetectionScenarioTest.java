package mr.xe.delid.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.core.ServiceContainer;
import mr.xe.delid.dto.DetectedLicenses;
import mr.xe.delid.dto.PurlDescriptor;
import mr.xe.delid.service.PurlStringParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@DisplayName("PackageLicenseDetectionScenario: определение лицензий пакетов всех типов")
class PackageLicenseDetectionScenarioTest {

  private static final String MANIFEST_RESOURCE = "/artifactory/manifest.json";
  /** Substring the fixture paths of {@code manifest.json} start with, so they become classpath URIs. */
  private static final String FIXTURE_PATH_PREFIX = "src/test/resources";

  /**
   * Identity of a fixture package: {@code <type>/<qualified name>@<version>}.
   *
   * <p>{@code manifest.json} and a PURL describe the same package differently, so the qualified name is
   * matched against several candidates on both sides, see {@link #qualifiedNameCandidates}.
   */
  private record PackageKey(String type, String qualifiedName, String version) {
  }

  /** Fixture of the fake artifactory: a file served at {@code uri} with an exact {@code Content-Type}. */
  private record Fixture(String uri, String contentType) {
  }

  /** Identity of a fixture package -> classpath URI of the artifact it is stored in. */
  private static final Map<PackageKey, String> FIXTURE_URIS_BY_PACKAGE = new LinkedHashMap<>();
  /** classpath URI -> fixture. */
  private static final Map<String, Fixture> FIXTURES_BY_URI = new LinkedHashMap<>();

  private static HttpServer artifactory;

  /**
   * Three packages of every {@code PackageType}.
   *
   * <p>The expectation is either the license identifier the detector is expected to report, or an
   * empty set for {@code DOCKER} and {@code RAW}, whose detectors are stubs inherited from
   * {@code AbstractPackageLicenseDetector} and can never report a license.
   */
  static Stream<Arguments> packagesWithExpectedLicenses() {
    return Stream.of(
        // MAVEN: license names are taken verbatim from the <licenses> block of the POM
        Arguments.of("pkg:maven/junit/junit@4.13.2", Set.of("Eclipse Public License 1.0")),
        Arguments.of("pkg:maven/com.google.code.gson/gson@2.8.9", Set.of("Apache-2.0")),
        Arguments.of("pkg:maven/jakarta.annotation/jakarta.annotation-api@3.0.0", Set.of("EPL 2.0", "GPL2 w/ CPE")),

        // PYPI: the License: field of the wheel METADATA
        Arguments.of("pkg:pypi/six@1.16.0", Set.of("MIT")),
        Arguments.of("pkg:pypi/markupsafe@2.1.5", Set.of("BSD-3-Clause")),
        Arguments.of("pkg:pypi/certifi@2023.11.17", Set.of("MPL-2.0")),

        // NPM: the license field of package.json
        Arguments.of("pkg:npm/lodash@4.17.21", Set.of("MIT")),
        Arguments.of("pkg:npm/ms@2.1.2", Set.of("MIT")),
        Arguments.of("pkg:npm/left-pad@1.3.0", Set.of("WTFPL")),

        // GO: no manifest carries a license, so the LICENSE file is matched against the SPDX corpus.
        // Only texts that score at least TEXT_MATCH_FACTOR (94) are recognised; these three were
        // picked because they clear it. See the note on near misses below.
        Arguments.of("pkg:go/github.com/stretchr/testify@v1.8.4", Set.of("MIT")),
        Arguments.of("pkg:go/github.com/go-logr/logr@v1.4.1", Set.of("Apache-2.0")),
        Arguments.of("pkg:go/github.com/hashicorp/go-multierror@v1.1.1", Set.of("MPL-2.0")),

        // NUGET: the <license> element of the .nuspec
        Arguments.of("pkg:nuget/Newtonsoft.Json@12.0.3", Set.of("MIT")),
        Arguments.of("pkg:nuget/Serilog@2.12.0", Set.of("Apache-2.0")),
        Arguments.of("pkg:nuget/NLog@5.3.4", Set.of("BSD-3-Clause")),

        // CARGO: the license key of Cargo.toml, reported verbatim, SPDX expressions not resolved
        Arguments.of("pkg:cargo/anyhow@1.0.86", Set.of("MIT OR Apache-2.0")),
        Arguments.of("pkg:cargo/cfg-if@1.0.0", Set.of("MIT/Apache-2.0")),
        Arguments.of("pkg:cargo/once_cell@1.19.0", Set.of("MIT OR Apache-2.0")),

        // DOCKER and RAW: stub detectors, they always report nothing
        Arguments.of("pkg:docker/library/hello-world@latest", Set.of()),
        Arguments.of("pkg:docker/library/alpine@3.19", Set.of()),
        Arguments.of("pkg:docker/library/busybox@1.36", Set.of()),
        Arguments.of("pkg:raw/MIT@1.0", Set.of()),
        Arguments.of("pkg:raw/Apache-2.0@1.0", Set.of()),
        Arguments.of("pkg:raw/BSD-3-Clause@1.0", Set.of()));
  }

  @BeforeAll
  static void startFakeArtifactory() throws IOException {
    // the scenario expects the services to be already initialised, exactly as App does it
    ServiceContainer.init();
    readManifest();

    artifactory = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    artifactory.createContext("/", PackageLicenseDetectionScenarioTest::serveFixture);
    artifactory.setExecutor(null);
    artifactory.start();
    log.info("Fake artifactory started on {}, {} fixtures", baseUrl(), FIXTURES_BY_URI.size());
  }

  @AfterAll
  static void stopFakeArtifactory() {
    if (artifactory != null) {
      artifactory.stop(0);
      artifactory = null;
    }
    FIXTURES_BY_URI.clear();
    FIXTURE_URIS_BY_PACKAGE.clear();
  }

  /**
   * Reads {@code manifest.json} and remembers every package fixture: its PURL, the classpath URI of the
   * artifact and the exact {@code Content-Type} the detectors require.
   */
  private static void readManifest() throws IOException {
    try (InputStream manifestStream =
             PackageLicenseDetectionScenarioTest.class.getResourceAsStream(MANIFEST_RESOURCE)) {
      if (manifestStream == null) {
        throw new IllegalStateException(format("Fixture manifest %s is not on the classpath", MANIFEST_RESOURCE));
      }
      final JsonNode packages = new ObjectMapper().readTree(manifestStream).get("packages");
      if (packages == null) {
        throw new IllegalStateException(format("Fixture manifest %s has no \"packages\" section", MANIFEST_RESOURCE));
      }
      for (JsonNode pkg : packages) {
        final String path = pkg.get("path").asText();
        if (!path.startsWith(FIXTURE_PATH_PREFIX + "/")) {
          throw new IllegalStateException(format("Unexpected fixture path %s in %s", path, MANIFEST_RESOURCE));
        }
        final String uri = path.substring(FIXTURE_PATH_PREFIX.length());
        FIXTURES_BY_URI.put(uri, new Fixture(uri, pkg.get("contentType").asText()));
        for (PackageKey key : packageKeysOf(pkg)) {
          FIXTURE_URIS_BY_PACKAGE.putIfAbsent(key, uri);
        }
      }
    }
  }

  /**
   * All qualified names a package may be addressed by, from the most to the least specific.
   *
   * <p>{@code manifest.json} and a PURL split the namespace and the name of a package at different
   * places: the PURL parser stops at the first slash, so {@code pkg:go/github.com/stretchr/testify@v1.8.4}
   * gives the namespace {@code github.com} and the name {@code stretchr/testify}, while the manifest entry
   * has the namespace {@code github.com/stretchr/testify} and the name {@code testify}. A fixture is
   * therefore registered under, and looked up by, every name that either side may produce.
   */
  private static List<String> qualifiedNameCandidates(String namespace, String name) {
    final List<String> candidates = new ArrayList<>();
    if (namespace != null && !namespace.isBlank()) {
      candidates.add(namespace);
      candidates.add(namespace + "/" + name);
    }
    candidates.add(name);
    return candidates;
  }

  /** Every key a {@code manifest.json} entry may be found by. */
  private static List<PackageKey> packageKeysOf(JsonNode pkg) {
    final JsonNode namespace = pkg.get("namespace");
    final String type = pkg.get("type").asText();
    final String version = pkg.get("version").asText();
    return qualifiedNameCandidates(namespace == null || namespace.isNull() ? null : namespace.asText(),
        pkg.get("name").asText()).stream()
        .map(qualifiedName -> new PackageKey(type, qualifiedName, version))
        .collect(Collectors.toList());
  }

  /** Every key a package addressed by its PURL may be found by. */
  private static List<PackageKey> packageKeysOf(String purl) {
    final PurlDescriptor descriptor = PurlStringParser.parse(purl);
    final String type = descriptor.type().toString().toLowerCase();
    return qualifiedNameCandidates(descriptor.namespace(), descriptor.name()).stream()
        .map(qualifiedName -> new PackageKey(type, qualifiedName, descriptor.version()))
        .collect(Collectors.toList());
  }

  /** Fixture URI of the package addressed by the given PURL, or {@code null} when there is no fixture. */
  private static String fixtureUriOf(String purl) {
    return packageKeysOf(purl).stream()
        .map(FIXTURE_URIS_BY_PACKAGE::get)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * Serves a fixture by its classpath URI. The {@code Content-Type} is sent exactly as declared in the
   * manifest, because the detectors compare it with {@code HttpResponseUtils.contentTypeCheck}.
   */
  private static void serveFixture(HttpExchange exchange) throws IOException {
    final String uri = exchange.getRequestURI().getPath();
    final Fixture fixture = FIXTURES_BY_URI.get(uri);
    if (fixture == null) {
      respondEmpty(exchange, 404);
      return;
    }
    final byte[] content;
    try (InputStream artifactStream = PackageLicenseDetectionScenarioTest.class.getResourceAsStream(uri)) {
      if (artifactStream == null) {
        respondEmpty(exchange, 404);
        return;
      }
      content = artifactStream.readAllBytes();
    }
    exchange.getResponseHeaders().set("Content-Type", fixture.contentType());
    exchange.sendResponseHeaders(200, content.length);
    try (OutputStream responseBody = exchange.getResponseBody()) {
      responseBody.write(content);
    }
  }

  private static void respondEmpty(HttpExchange exchange, int statusCode) throws IOException {
    log.warn("Fake artifactory has no fixture for {}", exchange.getRequestURI().getPath());
    exchange.sendResponseHeaders(statusCode, -1);
    exchange.close();
  }

  private static String baseUrl() {
    return format("http://127.0.0.1:%d", artifactory.getAddress().getPort());
  }

  /** URL of the fixture of the given package, or a failed assertion when the manifest has no such package. */
  private static String repositoryUrlOf(String purl) {
    final String uri = fixtureUriOf(purl);
    if (uri == null) {
      return fail(format("No fixture for purl %s in %s, known fixtures: %s",
          purl, MANIFEST_RESOURCE, FIXTURE_URIS_BY_PACKAGE.keySet()));
    }
    return baseUrl() + uri;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("packagesWithExpectedLicenses")
  @DisplayName("Лицензии пакета определяются и попадают в лог")
  void shouldDetectLicensesOfPackage(String purl, Set<String> expectedLicenseIds) {

    // given
    final String repositoryUrl = repositoryUrlOf(purl);

    // when
    final Set<DetectedLicenses.LicenseDescriptor> licenses =
        PackageLicenseDetectionScenario.detectLicenses(purl, repositoryUrl);

    // then
    final Set<String> actualLicenseIds = licenses.stream()
        .map(DetectedLicenses.LicenseDescriptor::getLicenseId)
        .collect(Collectors.toSet());
    log.info("Purl {}: licenses detected: {}", purl, actualLicenseIds);
    assertEquals(expectedLicenseIds, actualLicenseIds,
        () -> format("Purl %s: licenses detected from %s", purl, repositoryUrl));
  }

}

