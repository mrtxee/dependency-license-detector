package mr.xe.delid.api;

import java.io.InputStream;
import java.net.http.HttpResponse;
import mr.xe.delid.dto.PurlDescriptor;

/**
 * Artifactory REST client.
 * <a href="https://docs.sbt/draft/std/ext/ARR/2.20.0/common/apis/RGSE-RGSE-API-2.20.0/index.html">
 * REST API Artifactory</a>
 */
public interface PurlRepositoryClient {

  /**
   * Downloads asset with the given url.
   *
   * @param downloadUrl url
   */
  HttpResponse<InputStream> fetchHttpResponseStream(String downloadUrl);
}
