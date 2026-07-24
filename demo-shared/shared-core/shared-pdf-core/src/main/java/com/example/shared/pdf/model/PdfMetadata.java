package com.example.shared.pdf.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PDF 元数据模型
 * 充血模式：包含合并逻辑
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfMetadata {
  private String title;
  private String author;
  private String subject;
  private String keywords;
  private String creator;

  /**
   * 合并元数据
   * 逻辑：以当前对象（通常是全局配置）为基准，用传入的对象（通常是本次请求）进行覆盖。
   * 如果传入的对象字段为空，则保留当前对象的字段。
   *
   * @param override 本次请求的特定元数据（可以为空）
   * @return 合并后的新元数据对象
   */
  public PdfMetadata merge(PdfMetadata override) {
    if (override == null) {
      return this;
    }
    return PdfMetadata.builder()
      .title(override.getTitle() != null ? override.getTitle() : this.title)
      .author(override.getAuthor() != null ? override.getAuthor() : this.author)
      .subject(override.getSubject() != null ? override.getSubject() : this.subject)
      .keywords(override.getKeywords() != null ? override.getKeywords() : this.keywords)
      .creator(override.getCreator() != null ? override.getCreator() : this.creator)
      .build();
  }
}
