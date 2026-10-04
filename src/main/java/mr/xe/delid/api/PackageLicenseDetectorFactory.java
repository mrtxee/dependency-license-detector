package mr.xe.delid.api;

import mr.xe.delid.enums.PackageType;

/**
 * Factory of package repository implementations
 */
public interface PackageLicenseDetectorFactory {

  /**
   * Returns the target implementation for the given context.
   */
  AbstractPackageLicenseDetector get(PackageType type);
}
