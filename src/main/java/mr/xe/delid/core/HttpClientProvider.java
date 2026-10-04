package mr.xe.delid.core;

import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Creates and provides HttpClient instances
 */
public class HttpClientProvider extends AbstractUtility {

  public static HttpClient getArtifactoryHttpClient() {
    return ArtifactoryHttpClientHolder.INSTANCE;
  }

  private static SSLContext getTrustAllSslContext() throws NoSuchAlgorithmException, KeyManagementException {
    final SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, new TrustManager[] {
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
          }

          @Override
          public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
          }

          @Override
          public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[0];
          }
        }
    }, new SecureRandom());
    return sslContext;
  }

  private static HttpClient initArtifactoryHttpClient() {
    try {
      return HttpClient.newBuilder().sslContext(getTrustAllSslContext()).version(HttpClient.Version.HTTP_1_1)
          .followRedirects(HttpClient.Redirect.ALWAYS).build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to init ArtifactoryHttpClient", e);
    }
  }

  private static final class ArtifactoryHttpClientHolder {
    private static final HttpClient INSTANCE = initArtifactoryHttpClient();
  }

}
