package com.example.consumer.application.pdf.model;

import com.example.shared.pdf.model.BasePdfContext;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * GovNoticeContext
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/24 14:28
 */
@Getter
@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class GovNoticeContext extends BasePdfContext {
  private String orgName; // 发文机关 (如: 信息技术部)
  private String docNo;   // 发文字号 (如: 信发[2026] 8号)
  private String mainTitle; // 正文大标题
  private String bodyHtml;  // 正文内容 (支持 HTML 格式)

  @Override
  public String getTemplateName() {
    return "red-header";
  }
}
