package mr.xe.delid.service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.api.PurlRepositoryClient;
import mr.xe.delid.core.HttpClientProvider;
import static java.lang.String.format;

@Slf4j
public class PurlRepositoryClientImpl implements PurlRepositoryClient {
  public static final int HTTP_STATUS_CODE_OK = 200;
  private final HttpClient artifactoryHttpClient;

  @Override
  public HttpResponse<InputStream> fetchHttpResponseStream(String downloadUrl) {
    final HttpRequest request = withHeaders(HttpRequest.newBuilder().uri(URI.create(downloadUrl))).GET().build();
    final HttpResponse<InputStream> response;
    try {
      response = artifactoryHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (Exception e) {
      throw new IllegalStateException(format("Failed request to %s", downloadUrl));
    }
    if (response.statusCode() != HTTP_STATUS_CODE_OK) {
      throw new IllegalStateException(format("Bad response code %s for %s", response.statusCode(), downloadUrl));
    }

    return response;
  }

  private HttpRequest.Builder withHeaders(HttpRequest.Builder requestBuilder) {
    return requestBuilder
        .header("Accept", "application/json");
  }

  public PurlRepositoryClientImpl() {
    this.artifactoryHttpClient = HttpClientProvider.getArtifactoryHttpClient();
  }

}
