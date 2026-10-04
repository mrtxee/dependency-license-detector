package mr.xe.delid.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import mr.xe.delid.core.AbstractUtility;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;

public class FuzzyTextMatcher extends AbstractUtility {

  private static final Pattern COPYRIGHT_PATTERN = Pattern.compile("(?i)(copyright|©|\\(c\\))[^\\n]*\\n?");
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zа-яё0-9\\s]");
  private static final Pattern WHITESPACES = Pattern.compile("\\s+");
  private static final byte MAX_MATCH = 100;
  private static final double HASHSET_INITIAL_CAPACITY_FACTOR = 0.75d;

  /**
   * Calculates the similarity ratio of two license texts, in percent,
   * using the Sorensen-Dice coefficient over word bigrams.
   * <a href="https://en.wikipedia.org/wiki/Dice-S%C3%B8rensen_coefficient">Dice coefficient</a>
   *
   * @param referenceText reference license text
   * @param candidateText license text being matched
   * @return similarity ratio from 0 to 100 (an integer)
   */
  public static byte calculateDiceSimilarity(String referenceText, String candidateText) {
    if (referenceText == null || candidateText == null) {
      throw new IllegalArgumentException("Null input is forbidden");
    }

    final String normalizedReferenceText = normalize(referenceText);
    final String normalizedCandidateText = normalize(candidateText);

    if (normalizedReferenceText.isEmpty() && normalizedCandidateText.isEmpty()) {
      return MAX_MATCH;
    }
    if (normalizedReferenceText.isEmpty() || normalizedCandidateText.isEmpty()) {
      return 0;
    }

    final Set<String> referenceTextBigrams = buildWordBigrams(normalizedReferenceText);
    final Set<String> candidateTextBigrams = buildWordBigrams(normalizedCandidateText);

    return (byte) Math.round(applyDiceFormula(referenceTextBigrams, candidateTextBigrams) * MAX_MATCH);
  }

  /**
   * Dice formula for the bigram similarity ratio: (2 * |A ∩ B|) / (|A| + |B|)
   *
   * @param referenceTextBigrams bigrams of set A
   * @param candidateTextBigrams bigrams of set B
   * @return similarity ratio of the bigrams
   */
  private static double applyDiceFormula(Set<String> referenceTextBigrams, Set<String> candidateTextBigrams) {
    final Set<String> smallerBigrams = referenceTextBigrams.size() <= candidateTextBigrams.size()
        ? referenceTextBigrams
        : candidateTextBigrams;
    final Set<String> largerBigrams = referenceTextBigrams.size() <= candidateTextBigrams.size()
        ? candidateTextBigrams
        : referenceTextBigrams;

    int intersectionSize = 0;
    for (String bigram : smallerBigrams) {
      if (largerBigrams.contains(bigram)) {
        intersectionSize++;
      }
    }
    return (2.0 * intersectionSize) / (referenceTextBigrams.size() + candidateTextBigrams.size());
  }

  /**
   * Normalizes the text: drops the copyright notice, lowercases, removes punctuation and extra spaces.
   */
  private static String normalize(String text) {
    if (text.isEmpty()) {
      return StringUtils.EMPTY;
    }
    final String noCopyright = COPYRIGHT_PATTERN.matcher(text).replaceAll(EMPTY);
    final String lower = noCopyright.toLowerCase();
    final String cleaned = NON_ALPHANUMERIC.matcher(lower).replaceAll(SPACE);
    return WHITESPACES.matcher(cleaned).replaceAll(SPACE).trim();
  }

  /**
   * Builds the set of word bigrams from the normalized text.
   * Uses a HashSet for O(1) lookup when intersecting.
   * Estimates the set size upfront to avoid re-hashing.
   * Estimated initial HashSet capacity: bigram count = words.length - 1
   * Default load factor is 0.75, so capacity = (n / 0.75) + 1
   */
  private static Set<String> buildWordBigrams(String normalizedText) {
    final String[] words = normalizedText.split(SPACE);
    if (words.length < 2) {
      return Set.of();
    }
    final int expectedSize = words.length - 1;
    final int capacity = (int) (expectedSize / HASHSET_INITIAL_CAPACITY_FACTOR) + 1;
    final Set<String> bigrams = new HashSet<>(capacity);

    for (int i = 0; i < words.length - 1; i++) {
      bigrams.add(words[i] + SPACE + words[i + 1]);
    }

    return bigrams;
  }

}
