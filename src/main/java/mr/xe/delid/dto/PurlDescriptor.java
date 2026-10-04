package mr.xe.delid.dto;

import lombok.NonNull;
import mr.xe.delid.enums.PackageType;

/**
 * Product dependency descriptor. Examples of valid values:
 * pkg:<type>/<namespace>/<name>@<version>?<qualifiers>#<subpath>
 * "maven/com.clickhouse/clickhouse-jdbc@0.7.0",
 * "pypi/pyyaml@6.0.1",
 * "rpm/libstdc++6@6.0.8"
 *
 * <p>The repository URL is not a part of the package identity: it only tells where the package is
 * fetched from, therefore it is excluded from {@link #purl()} and {@link #getFileName()}. A null or
 * blank value means that the repository configured at application startup is used.
 */
public record PurlDescriptor(
    @NonNull PackageType type,
    @NonNull String name,
    String namespace,
    String version,
    String repositoryUrl
) {

  /**
   * Creates a descriptor of a package whose repository is not specified explicitly.
   * The repository configured at application startup is used for such a package.
   *
   * @param type      package type
   * @param name      package name
   * @param namespace package namespace (group), may be null
   * @param version   package version, may be null
   */
  public PurlDescriptor(@NonNull PackageType type, @NonNull String name, String namespace, String version) {
    this(type, name, namespace, version, null);
  }

  public @NonNull String purl() {
    final StringBuilder sb = new StringBuilder("pkg:").append(type.toString().toLowerCase());
    if (namespace != null) {
      sb.append("/").append(namespace);
    }
    sb.append("/").append(name);
    if (version != null) {
      sb.append("@").append(version);
    }
    return sb.toString();
  }

  public @NonNull String getFileName() {
    final String delimiter = "_";
    final StringBuilder sb = new StringBuilder(type.toString().toLowerCase());
    if (namespace != null) {
      sb.append(delimiter).append(namespace);
    }
    sb.append(delimiter).append(name);
    if (version != null) {
      sb.append(delimiter).append(version);
    }
    return sb.toString();
  }
}
