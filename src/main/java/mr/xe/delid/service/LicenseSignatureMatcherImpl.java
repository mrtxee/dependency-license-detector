package mr.xe.delid.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.api.LicenseSignatureMatcher;
import mr.xe.delid.util.FileUtils;
import mr.xe.delid.util.FuzzyTextMatcher;
import static mr.xe.delid.config.ApplicationConfig.TEXT_MATCH_FACTOR;
import static mr.xe.delid.config.ApplicationConfig.UNIDENTIFIED_LICENSE;

@Slf4j
public class LicenseSignatureMatcherImpl implements LicenseSignatureMatcher {

  private static final String SPDX_REFERENCES_PATH = "spdx/references/";
  private static final String BEST_MATCH_MESSAGE = "Best match is {}% on {} for {}:{}";
  private final List<ReferenceSpdx> referenceSpdxes;

  public LicenseSignatureMatcherImpl() {
    referenceSpdxes = new ArrayList<>();
    initReferenceSpdxes();
  }

  @Override
  public @NonNull String findMatchLicenseId(String purl, String filePath, String needleText) {
    int maxMatchFactor = 0;
    String maxMatchFactorName = null;
    for (ReferenceSpdx referenceSpdx : referenceSpdxes) {
      final int matchFactor = FuzzyTextMatcher.calculateDiceSimilarity(referenceSpdx.text(), needleText);
      if (matchFactor >= TEXT_MATCH_FACTOR) {
        log.info(BEST_MATCH_MESSAGE, matchFactor, referenceSpdx.spdxId(), purl, filePath);
        return referenceSpdx.spdxId();
      }
      if (maxMatchFactor < matchFactor) {
        maxMatchFactor = matchFactor;
        maxMatchFactorName = referenceSpdx.spdxId();
      }
    }
    log.warn(BEST_MATCH_MESSAGE, maxMatchFactor, maxMatchFactorName, purl, filePath);
    return UNIDENTIFIED_LICENSE;
  }

  private void initReferenceSpdxes() {
    FileUtils.listClasspathDir(SPDX_REFERENCES_PATH).forEach(path ->
        referenceSpdxes.add(
            new ReferenceSpdx(
                path,
                FileUtils.removeExtension(path),
                FileUtils.readFileAsString(path)
            )
        )
    );
  }

  private record ReferenceSpdx(
      Path filePath,
      String spdxId,
      String text
  ) {
  }

}
