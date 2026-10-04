package mr.xe.delid.dto;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Detected licenses
 */
public record DetectedLicenses(
    String purl,
    Set<? extends LicenseDescriptor> licenses
) {

  @Getter
  @AllArgsConstructor
  public static class LicenseDescriptor {
    private final String licenseId;
    private final String source;
  }

}
