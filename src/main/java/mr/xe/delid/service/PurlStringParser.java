package mr.xe.delid.service;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.core.AbstractUtility;
import mr.xe.delid.dto.PurlDescriptor;
import mr.xe.delid.enums.PackageType;
import static java.lang.String.format;

/**
 * PURL parser.
 * PURL format: `pkg:<type>/<namespace>/<name>@<version>`.
 */
@Slf4j
public final class PurlStringParser extends AbstractUtility {

  private static final String INVALID_PURL_MESSAGE = "Invalid package name: %s. %s.";
  private static final String NAME_CANNOT_BE_EMPTY_MESSAGE = "Name cannot be empty";
  private static final String PURL_PACKAGE_PREFIX = "pkg:";

  public static List<PurlDescriptor> parsePackages(List<String> packages) {
    if (packages == null) {
      throw new IllegalArgumentException("Input list cannot be null");
    }

    final List<PurlDescriptor> purlDescriptors = new ArrayList<>();
    packages.forEach(packageName -> {
      try {
        purlDescriptors.add(parse(packageName));
      } catch (Exception e) {
        log.error("Invalid package name: {}", packageName, e);
      }
    });

    return purlDescriptors;
  }

  public static PurlDescriptor parse(String packageName) {
    return parse(packageName, null);
  }

  /**
   * Parses a package name and binds it to the repository it is fetched from.
   *
   * @param packageName   package name in the PURL format
   * @param repositoryUrl repository base URL; null or blank means the repository configured at
   *                      application startup
   */
  public static PurlDescriptor parse(String packageName, String repositoryUrl) {
    if (packageName == null) {
      throw new IllegalArgumentException("Package name cannot be null");
    }

    if (!packageName.startsWith(PURL_PACKAGE_PREFIX)) {
      throw new IllegalArgumentException(
          format(INVALID_PURL_MESSAGE, packageName, format("Must start with %s", PURL_PACKAGE_PREFIX)));
    }

    final String content = packageName.substring("pkg:".length());
    if (content.isEmpty()) {
      throw new IllegalArgumentException(
          format(INVALID_PURL_MESSAGE, packageName, format("No content after %s", PURL_PACKAGE_PREFIX)));

    }
    final int firstSlash = content.indexOf('/');
    if (firstSlash == -1) {
      throw new IllegalArgumentException(
          format(INVALID_PURL_MESSAGE, packageName, "Missing '/' after type."));
    }

    final PackageType type = PackageType.fromString(content.substring(0, firstSlash))
        .orElseThrow(() -> new IllegalArgumentException(
            format(INVALID_PURL_MESSAGE, packageName, "Type cannot be empty.")));

    final String rest = content.substring(firstSlash + 1);
    if (rest.isEmpty()) {
      throw new IllegalArgumentException(format(INVALID_PURL_MESSAGE, packageName, "Missing name."));
    }

    final int atIndex = rest.lastIndexOf('@');
    final String nameAndGroup;
    String version;

    if (atIndex == -1) {
      nameAndGroup = rest;
      version = null;
    } else if (atIndex == rest.length() - 1) {
      nameAndGroup = rest.substring(0, atIndex);
      version = null;
    } else {
      nameAndGroup = rest.substring(0, atIndex);
      version = rest.substring(atIndex + 1);
      if (version.isEmpty()) {
        version = null;
      }
    }

    final int secondSlash = nameAndGroup.indexOf('/');
    String group;
    final String name;

    if (secondSlash != -1) {
      group = nameAndGroup.substring(0, secondSlash);
      name = nameAndGroup.substring(secondSlash + 1);
      if (name.isEmpty()) {
        throw new IllegalArgumentException(
            format(INVALID_PURL_MESSAGE, packageName, NAME_CANNOT_BE_EMPTY_MESSAGE));
      }
      if (group.isEmpty()) {
        group = null;
      }
    } else {
      group = null;
      name = nameAndGroup;
      if (name.isEmpty()) {
        throw new IllegalArgumentException(
            format(INVALID_PURL_MESSAGE, packageName, NAME_CANNOT_BE_EMPTY_MESSAGE));
      }
    }
    return new PurlDescriptor(type, name, group, version, repositoryUrl);
  }

}
