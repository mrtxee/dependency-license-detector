package mr.xe.delid.util.detector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for NPM packages
 */
@Slf4j
@RequiredArgsConstructor
public class NpmPackageParser {

  private final ObjectMapper mapper;

  public Optional<String> extractLicense(Path packageJsonPath) {
    if (packageJsonPath == null || !Files.exists(packageJsonPath) || !Files.isReadable(packageJsonPath)) {
      return Optional.empty();
    }

    try {
      final JsonNode rootNode = mapper.readTree(packageJsonPath.toFile());
      final JsonNode licenseNode = rootNode.get("license");

      if (licenseNode != null && licenseNode.isTextual()) {
        return Optional.of(licenseNode.asText());
      }

      if (licenseNode != null && licenseNode.isObject() && licenseNode.has("type")) {
        return Optional.of(licenseNode.get("type").asText());
      }

    } catch (Exception e) {
      log.error("Error parsing license", e);
    }

    return Optional.empty();
  }
}
