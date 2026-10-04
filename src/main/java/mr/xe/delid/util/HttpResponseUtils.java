package mr.xe.delid.util;

import java.net.http.HttpResponse;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.core.AbstractUtility;
import static java.lang.String.format;

@Slf4j
public final class HttpResponseUtils extends AbstractUtility {

  public static String getContentType(HttpResponse<?> response) {
    return response.headers().firstValue("Content-Type").orElse("?");
  }

  public static void contentTypeCheck(HttpResponse<?> response, String expectedContentType) {
    contentTypeCheck(response, Set.of(expectedContentType));
  }

  public static void contentTypeCheck(HttpResponse<?> response, Set<String> expectedContentTypes) {
    final String actualContentType = getContentType(response);
    final boolean isValid = expectedContentTypes.stream()
        .anyMatch(expectedContentType -> expectedContentType.equals(actualContentType));
    if (isValid) {
      return;
    }
    throw new IllegalStateException(
        format("Invalid Content-type %s, expected %s for %s", actualContentType, expectedContentTypes, response.uri()));
  }
}
