package com.alexandria.app.knowledgebase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileNameValidatorTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "report.pdf",
        "quarterly-report_v2.docx",
        "notes 2024.txt",
        "resume (final).pdf",
        "a.b.c.gz"
      })
  void unit_fnv_acceptsSafeNames(String name) {
    assertThatCode(() -> FileNameValidator.validate(name)).doesNotThrowAnyException();
  }

  @Test
  void unit_fnv_rejectsNull() {
    assertThatThrownBy(() -> FileNameValidator.validate(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "\n"})
  void unit_fnv_rejectsBlank(String name) {
    assertThatThrownBy(() -> FileNameValidator.validate(name))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "../etc/passwd",
        "..",
        ".",
        "a/b.txt",
        "a\\b.txt",
        "evil\u0000.pdf",
        "bell\u0007.pdf",
        "del\u007F.pdf",
        "html<script>.txt",
        "pipe|rm.txt",
        "quote\".txt",
        "star*.txt",
        "colon:name.txt",
        "question?.txt"
      })
  void unit_fnv_rejectsInjectionAndTraversal(String name) {
    assertThatThrownBy(() -> FileNameValidator.validate(name))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {" leading.txt", "trailing.txt ", "trailing.dot."})
  void unit_fnv_rejectsWhitespaceAndTrailingDot(String name) {
    assertThatThrownBy(() -> FileNameValidator.validate(name))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CON", "con.txt", "PRN.pdf", "NUL", "COM1.log", "lpt9.dat"})
  void unit_fnv_rejectsReservedNames(String name) {
    assertThatThrownBy(() -> FileNameValidator.validate(name))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unit_fnv_rejectsOversizedNames() {
    String longName = "a".repeat(FileNameValidator.MAX_LENGTH + 1);
    assertThatThrownBy(() -> FileNameValidator.validate(longName))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unit_fnv_acceptsExactMaxLength() {
    String maxName = "a".repeat(FileNameValidator.MAX_LENGTH);
    assertThatCode(() -> FileNameValidator.validate(maxName)).doesNotThrowAnyException();
  }
}
