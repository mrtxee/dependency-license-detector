package mr.xe.delid.api;

import java.util.Set;
import mr.xe.delid.dto.DetectedLicenses;
import mr.xe.delid.dto.PurlDescriptor;

/**
 * Contract for a package repository
 */
public interface PackageLicenseDetector {

  /**
   * Returns the list of package licenses
   */
  Set<DetectedLicenses.LicenseDescriptor> fetchLicenses(PurlDescriptor purlDescriptor);

}
