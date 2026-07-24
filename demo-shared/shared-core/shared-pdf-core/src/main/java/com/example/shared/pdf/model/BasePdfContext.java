package com.example.shared.pdf.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 基础上下文，包含元数据
 * 所有的业务 PDF 请求都应该继承此类
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/24 10:06
 */

@Getter
@SuperBuilder
public abstract class BasePdfContext {

  /**
   * 单次请求的元数据覆盖 (可选)
   * 业务方可以在构造时设置，用于覆盖全局默认的 Title/Author 等
   */
  @JsonIgnore // 转 Map 时忽略此字段，避免污染业务数据
  private PdfMetadata metadata;

  /**
   * 绑定模板名称 (由子类实现)
   * 例如: return "contract";
   */
  @JsonIgnore // 转 Map 时忽略
  public abstract String getTemplateName();

}
