package mr.xe.delid.api;

/**
 * Service that matches text signatures
 */
public interface LicenseSignatureMatcher {

  /**
   * Returns the identifier of the matched text
   *
   * @param filePath path to the file holding the reference text
   * @param needleText text being matched
   * @return identifier of the matched text
   */
  String findMatchLicenseId(String purl, String filePath, String needleText);
}
