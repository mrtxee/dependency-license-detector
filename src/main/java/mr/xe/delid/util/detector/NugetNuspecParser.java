package mr.xe.delid.util.detector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import mr.xe.delid.core.AbstractUtility;

@Slf4j
public final class NugetNuspecParser extends AbstractUtility {

  public static Optional<String> extractLicense(String nuspec) {
    if (StringUtils.isBlank(nuspec)) {
      return Optional.empty();
    }
    try (InputStream stream = new ByteArrayInputStream(nuspec.getBytes(StandardCharsets.UTF_8))) {
      return extractLicense(stream);
    } catch (IOException e) {
      log.error("Error processing nuspec string input", e);
    }
    return Optional.empty();
  }

  /**
   * Extracts the license value from a .nuspec file, ignoring the xmlns schema version.
   *
   * @param nuspecInputStream stream with the content of the .nuspec file
   * @return Optional holding the license value (for example, "MIT", "Apache-2.0", or a URL),
   *     or Optional.empty() if the tag is not found.
   */
  public static Optional<String> extractLicense(InputStream nuspecInputStream) {
    final XMLInputFactory factory = XMLInputFactory.newInstance();

    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

    XMLStreamReader reader = null;
    try {
      reader = factory.createXMLStreamReader(nuspecInputStream);
      while (reader.hasNext()) {
        int event = reader.next();

        if (event == XMLStreamConstants.START_ELEMENT) {
          String localName = reader.getLocalName();

          if ("license".equals(localName)) {
            return Optional.ofNullable(reader.getElementText());
          } else if ("licenseUrl".equals(localName)) {
            return Optional.ofNullable(reader.getElementText());
          }
        }
      }
    } catch (Throwable e) {
      log.error("Error parsing nuspec xml", e);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (Throwable e) {
          log.error("Error closing stream", e);
        }
      }
    }

    return Optional.empty();
  }

}

