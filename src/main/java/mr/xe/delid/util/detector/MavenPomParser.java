package mr.xe.delid.util.detector;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import mr.xe.delid.core.AbstractUtility;

/**
 * Parser for POM files of Maven packages
 */
public final class MavenPomParser extends AbstractUtility {

  private static final String MAVEN_NS = "http://maven.apache.org/POM/4.0.0";

  /**
   * Extracts every license.name value from a Maven POM.
   *
   * @param xmlStream InputStream with the POM file content
   * @return list of license names
   */
  public static Set<String> extractLicenseNames(InputStream xmlStream)
      throws IOException, ParserConfigurationException, SAXException, XPathExpressionException {

    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    final DocumentBuilder builder = factory.newDocumentBuilder();
    final Document doc = builder.parse(new InputSource(xmlStream));
    final Element root = doc.getDocumentElement();
    if (!MAVEN_NS.equals(root.getNamespaceURI())) {
      throw new RuntimeException(
          "XML не является Maven POM: ожидаемое пространство имен '" + MAVEN_NS +
              "', найдено '" + (root.getNamespaceURI() != null ? root.getNamespaceURI() : "null") + "'"
      );
    }

    final XPath xpath = XPathFactory.newInstance().newXPath();
    xpath.setNamespaceContext(new NamespaceContext() {
      @Override
      public String getNamespaceURI(String prefix) {
        if ("m".equals(prefix)) {
          return MAVEN_NS;
        }
        return javax.xml.XMLConstants.NULL_NS_URI;
      }

      @Override
      public String getPrefix(String namespaceUri) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Iterator<String> getPrefixes(String namespaceUri) {
        throw new UnsupportedOperationException();
      }
    });

    final String xpathExpression = "//m:licenses/m:license/m:name";
    final NodeList nodes = (NodeList) xpath.compile(xpathExpression).evaluate(doc, XPathConstants.NODESET);

    final Set<String> result = new HashSet<>(nodes.getLength());
    for (int i = 0; i < nodes.getLength(); i++) {
      final Element nameElement = (Element) nodes.item(i);
      final String text = nameElement.getTextContent();
      if (text != null) {
        result.add(text.trim());
      }
    }

    return result;
  }

}

