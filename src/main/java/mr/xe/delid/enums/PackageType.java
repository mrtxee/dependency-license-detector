package mr.xe.delid.enums;

import java.util.Optional;

/**
 * Supported package types
 */
public enum PackageType {
  MAVEN,
  PYPI,
  NPM,
  GO,
  NUGET,
  CARGO,
  DOCKER,
  RAW;

  public static Optional<PackageType> fromString(String format) {
    try {
      return Optional.of(valueOf(format.trim().toUpperCase()));
    } catch (Throwable e) {
      return Optional.empty();
    }
  }
}
