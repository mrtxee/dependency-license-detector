package mr.xe.delid.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import mr.xe.delid.util.FuzzyTextMatcher;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Тест методов неточного сравнения текстов")
class FuzzyTextMatcherTest {
  public static final String REFERENCE_TEXT = """
      Permission is hereby granted, free of charge, to any person obtaining a copy
      of this software and associated documentation files (the "Software"), to deal
      in the Software without restriction, including without limitation the rights
      to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
      copies of the Software, and to permit persons to whom the Software is
      furnished to do so, subject to the following conditions:
      The above copyright notice and this permission notice shall be included in all
      copies or substantial portions of the Software.
      THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
      """;

  @Test
  @DisplayName("Тот же текст, другой копирайт - совпадают")
  void sameTextMatch() {
    // given
    final String testText = """
        © Если вы читаете этот текст - вы и есть сопротивление
        Copyright John Conor
        (c) 2035
        
        """ + REFERENCE_TEXT;
    // when
    byte similarityFactor = FuzzyTextMatcher.calculateDiceSimilarity(REFERENCE_TEXT, testText);
    // than
    assertEquals(100, similarityFactor);
  }

  @Test
  @DisplayName("Добавлен дополнительный абзац - совпадают частично")
  void sameTextExtraParagraphMatchPartly() {
    // given
    final String testText = REFERENCE_TEXT + """
        
        ADDITIONAL CLAUSE: This software must not be used for evil purposes.
        """;
    // when
    byte similarityFactor = FuzzyTextMatcher.calculateDiceSimilarity(REFERENCE_TEXT, testText);
    // than
    assertEquals(94, similarityFactor);
  }


  @Test
  @DisplayName("Другой текст - не совпадают")
  void differentTextNotMatch() {
    // given
    final String testText = """
        GNU GENERAL PUBLIC LICENSE
        Version 3, 29 June 2007
        Everyone is permitted to copy and distribute verbatim copies
        of this license document, but changing it is not allowed.
        """;
    // when
    byte similarityFactor = FuzzyTextMatcher.calculateDiceSimilarity(REFERENCE_TEXT, testText);
    // than
    assertEquals(4, similarityFactor);
  }

}