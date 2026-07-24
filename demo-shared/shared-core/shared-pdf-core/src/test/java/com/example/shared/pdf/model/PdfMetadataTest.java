package com.example.shared.pdf.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PdfMetadataTest {

  @Test
  @DisplayName("合并：当覆盖对象为空时，应返回原对象")
  void merge_ShouldReturnOriginal_WhenOverrideIsNull() {
    PdfMetadata original = PdfMetadata.builder().title("Original").build();
    PdfMetadata result = original.merge(null);

    assertSame(original, result);
  }

  @Test
  @DisplayName("合并：应只覆盖非空字段")
  void merge_ShouldOverrideOnlyNonNullFields() {
    // Arrange
    PdfMetadata original = PdfMetadata.builder()
      .title("Old Title")
      .author("Old Author")
      .keywords("Old Key")
      .build();

    PdfMetadata override = PdfMetadata.builder()
      .title("New Title") // 覆盖
      .keywords(null)     // 不覆盖
      .build();

    // Act
    PdfMetadata result = original.merge(override);

    // Assert
    assertEquals("New Title", result.getTitle());
    assertEquals("Old Author", result.getAuthor()); // 保持原样
    assertEquals("Old Key", result.getKeywords());  // 保持原样
  }

  @Test
  @DisplayName("合并：当原字段为空时，应使用覆盖值")
  void merge_ShouldUseOverride_WhenOriginalIsNull() {
    PdfMetadata original = PdfMetadata.builder().title(null).build();
    PdfMetadata override = PdfMetadata.builder().title("New Title").build();

    PdfMetadata result = original.merge(override);

    assertEquals("New Title", result.getTitle());
  }
}
