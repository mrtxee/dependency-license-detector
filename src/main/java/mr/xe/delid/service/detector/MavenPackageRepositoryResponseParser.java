package mr.xe.delid.service.detector;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.util.HttpResponseUtils;

@Slf4j
public class MavenPackageRepositoryResponseParser {

  private static final String CONTENT_TYPE_JSON = "application/json";

  private final ObjectMapper objectMapper;

  public MavenPackageRepositoryResponseParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Validates the response and deserializes the body into an artifact list.
   *
   * @param response HTTP response whose body is an InputStream
   * @return List of ArtifactItem objects (empty if items are absent or null)
   * @throws IOException              if reading the stream failed
   * @throws IllegalArgumentException if Content-Type is not application/json
   */
  public List<MavenArtifactItem> parseMavenArtifactItems(HttpResponse<InputStream> response)
      throws IOException {

    HttpResponseUtils.contentTypeCheck(response, CONTENT_TYPE_JSON);

    try (InputStream inputStream = response.body()) {
      final MavenArtifactSearchResponse parsedResponse = objectMapper.readValue(
          inputStream,
          MavenArtifactSearchResponse.class
      );

      return parsedResponse.items() != null
          ? parsedResponse.items()
          : Collections.emptyList();
    }
  }

  public List<String> findDownloadUrlsByContentType(HttpResponse<InputStream> response, String contentType) {
    try {
      return parseMavenArtifactItems(response)
          .stream()
          .filter(mavenArtifactItem -> mavenArtifactItem.contentType().equals(contentType))
          .map(MavenArtifactItem::downloadUrl)
          .toList();
    } catch (IOException e) {
      log.error("Extracting download URLs error", e);
    }
    return List.of();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MavenArtifactSearchResponse(
      List<MavenArtifactItem> items,
      String continuationToken
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MavenArtifactItem(
      String downloadUrl,
      String contentType,
      OffsetDateTime lastModified,
      Long fileSize
  ) {
  }

}
