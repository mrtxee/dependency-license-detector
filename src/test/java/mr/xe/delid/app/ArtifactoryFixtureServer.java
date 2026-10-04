package mr.xe.delid.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.dto.PurlDescriptor;
import mr.xe.delid.service.PurlStringParser;
import static java.lang.String.format;

/**
 * Fake artifactory that serves the package fixtures described by
 * {@code src/test/resources/artifactory/manifest.json}.
 *
 * <p>Every detector downloads a package from {@link PurlDescriptor#repositoryUrl()}, so the fixtures cannot be
 * read from the classpath directly: they have to be served over HTTP. This class starts a {@link HttpServer} on a
 * free loopback port, serves each fixture at the classpath URI of its file with the exact {@code Content-Type}
 * declared in the manifest (the detectors compare it literally, see
 * {@code HttpResponseUtils.contentTypeCheck}), and resolves the download URL of a package.
 *
 * <p>Shared by {@link PackageLicenseDetectionScenarioTest} and {@link ManifestLicenseDetectionRunner}.
 */
@Slf4j
public final class ArtifactoryFixtureServer implements AutoCloseable {

  public static final String MANIFEST_RESOURCE = "/artifactory/manifest.json";
  /** Substring the fixture paths of {@code manifest.json} start with, so they become classpath URIs. */
  private static final String FIXTURE_PATH_PREFIX = "src/test/resources";

  /**
   * Identity of a fixture package: {@code <type>/<qualified name>@<version>}.
   *
   * <p>{@code manifest.json} and a PURL describe the same package differently, so the qualified name is matched
   * against several candidates on both sides, see {@link #qualifiedNameCandidates}.
   */
  private record PackageKey(String type, String qualifiedName, String version) {
  }

  /** Fixture of the fake artifactory: a file served at {@code uri} with an exact {@code Content-Type}. */
  private record Fixture(String uri, String contentType) {
  }

  /**
   * A package fixture of {@code manifest.json}.
   *
   * @param purl            PURL of the package, rebuilt from the manifest fields
   * @param registryLicense license the public registry reports; kept for reporting only, since it is what the
   *                        registry metadata says, not what a detector extracts from the artifact
   * @param repositoryUrl   URL of the fixture, valid while the server is running
   */
  public record ManifestPackage(
      String purl,
      String type,
      String namespace,
      String name,
      String version,
      String registryLicense,
      String repositoryUrl) {
  }

  /** Identity of a fixture package -> classpath URI of the artifact it is stored in. */
  private final Map<PackageKey, String> fixtureUrisByPackage = new LinkedHashMap<>();
  /** classpath URI -> fixture. */
  private final Map<String, Fixture> fixturesByUri = new LinkedHashMap<>();
  /** Packages of the manifest, in the order they are declared. */
  private final List<ManifestPackage> packages = new ArrayList<>();

  private final HttpServer server;

  private ArtifactoryFixtureServer(HttpServer server, List<ManifestPackage> packages,
      Map<PackageKey, String> fixtureUrisByPackage, Map<String, Fixture> fixturesByUri) {
    this.server = server;
    this.packages.addAll(packages);
    this.fixtureUrisByPackage.putAll(fixtureUrisByPackage);
    this.fixturesByUri.putAll(fixturesByUri);
  }

  /**
   * Reads the manifest and starts serving the fixtures it declares.
   *
   * @throws IllegalStateException if the manifest is missing or malformed, or declares a fixture path outside
   *                               {@code src/test/resources}
   */
  @SneakyThrows
  public static ArtifactoryFixtureServer start() {
    final List<ManifestPackage> packages = new ArrayList<>();
    final Map<PackageKey, String> fixtureUrisByPackage = new LinkedHashMap<>();
    final Map<String, Fixture> fixturesByUri = new LinkedHashMap<>();

    for (JsonNode pkg : readManifest().get("packages")) {
      final String path = pkg.get("path").asText();
      if (!path.startsWith(FIXTURE_PATH_PREFIX + "/")) {
        throw new IllegalStateException(format("Unexpected fixture path %s in %s", path, MANIFEST_RESOURCE));
      }
      final String uri = path.substring(FIXTURE_PATH_PREFIX.length());
      fixturesByUri.put(uri, new Fixture(uri, pkg.get("contentType").asText()));
      qualifiedNameCandidates(namespaceOf(pkg), pkg.get("name").asText()).stream()
          .map(qualifiedName -> new PackageKey(pkg.get("type").asText(), qualifiedName, pkg.get("version").asText()))
          .forEach(key -> fixtureUrisByPackage.putIfAbsent(key, uri));
      packages.add(new ManifestPackage(
          purlOf(pkg),
          pkg.get("type").asText(),
          namespaceOf(pkg),
          pkg.get("name").asText(),
          pkg.get("version").asText(),
          pkg.get("registryLicense").asText(),
          uri));
    }

    final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    final ArtifactoryFixtureServer artifactory =
        new ArtifactoryFixtureServer(server, packages, fixtureUrisByPackage, fixturesByUri);
    server.createContext("/", artifactory::serveFixture);
    server.setExecutor(null);
    server.start();
    log.info("Fake artifactory started on {}, {} fixtures, {} packages",
        artifactory.baseUrl(), fixturesByUri.size(), packages.size());
    return artifactory;
  }

  private static JsonNode readManifest() {
    try (InputStream manifestStream = ArtifactoryFixtureServer.class.getResourceAsStream(MANIFEST_RESOURCE)) {
      if (manifestStream == null) {
        throw new IllegalStateException(format("Fixture manifest %s is not on the classpath", MANIFEST_RESOURCE));
      }
      final JsonNode manifest = new ObjectMapper().readTree(manifestStream);
      if (manifest == null || manifest.get("packages") == null) {
        throw new IllegalStateException(format("Fixture manifest %s has no \"packages\" section", MANIFEST_RESOURCE));
      }
      return manifest;
    } catch (IOException e) {
      throw new IllegalStateException(format("Failed to read fixture manifest %s", MANIFEST_RESOURCE), e);
    }
  }

  private static String namespaceOf(JsonNode pkg) {
    final JsonNode namespace = pkg.get("namespace");
    return namespace == null || namespace.isNull() ? null : namespace.asText();
  }

  /** Rebuilds the PURL of a manifest entry, for example {@code pkg:maven/junit/junit@4.13.2}. */
  private static String purlOf(JsonNode pkg) {
    final String namespace = namespaceOf(pkg);
    return format("pkg:%s/%s%s@%s",
        pkg.get("type").asText(),
        namespace == null ? "" : namespace + "/",
        pkg.get("name").asText(),
        pkg.get("version").asText());
  }

  /**
   * All qualified names a package may be addressed by, from the most to the least specific.
   *
   * <p>{@code manifest.json} and a PURL split the namespace and the name of a package at different places: the PURL
   * parser stops at the first slash, so {@code pkg:go/github.com/stretchr/testify@v1.8.4} gives the namespace
   * {@code github.com} and the name {@code stretchr/testify}, while the manifest entry has the namespace
   * {@code github.com/stretchr/testify} and the name {@code testify}. A fixture is therefore registered under, and
   * looked up by, every name that either side may produce.
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

  /** Every key a package addressed by its PURL may be found by. */
  private static List<PackageKey> packageKeysOf(String purl) {
    final PurlDescriptor descriptor = PurlStringParser.parse(purl);
    final String type = descriptor.type().toString().toLowerCase();
    return qualifiedNameCandidates(descriptor.namespace(), descriptor.name()).stream()
        .map(qualifiedName -> new PackageKey(type, qualifiedName, descriptor.version()))
        .collect(Collectors.toList());
  }

  /** Fixture URI of the package addressed by the given PURL, or {@code null} when there is no fixture. */
  private String fixtureUriOf(String purl) {
    return packageKeysOf(purl).stream()
        .map(fixtureUrisByPackage::get)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * Download URL of the fixture of the given package.
   *
   * @throws IllegalArgumentException if the manifest has no fixture for the package
   */
  public String repositoryUrlOf(String purl) {
    final String uri = fixtureUriOf(purl);
    if (uri == null) {
      throw new IllegalArgumentException(format("No fixture for purl %s in %s, known fixtures: %s",
          purl, MANIFEST_RESOURCE, fixtureUrisByPackage.keySet()));
    }
    return baseUrl() + uri;
  }

  /** All packages of the manifest, in declaration order, with their fixture URLs resolved. */
  public List<ManifestPackage> packages() {
    return Collections.unmodifiableList(packages.stream()
        .map(pkg -> new ManifestPackage(pkg.purl(), pkg.type(), pkg.namespace(), pkg.name(), pkg.version(),
            pkg.registryLicense(), repositoryUrlOf(pkg.purl())))
        .collect(Collectors.toList()));
  }

  /** Base URL the fixtures are served from. */
  public String baseUrl() {
    return format("http://127.0.0.1:%d", server.getAddress().getPort());
  }

  /**
   * Serves a fixture by its classpath URI. The {@code Content-Type} is sent exactly as declared in the manifest,
   * because the detectors compare it with {@code HttpResponseUtils.contentTypeCheck}.
   */
  private void serveFixture(HttpExchange exchange) throws IOException {
    final String uri = exchange.getRequestURI().getPath();
    final Fixture fixture = fixturesByUri.get(uri);
    if (fixture == null) {
      respondEmpty(exchange, 404);
      return;
    }
    final byte[] content;
    try (InputStream artifactStream = ArtifactoryFixtureServer.class.getResourceAsStream(uri)) {
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

  @Override
  public void close() {
    server.stop(0);
    log.info("Fake artifactory stopped");
  }

}
