package com.example.consumer.application.pdf.model;

import com.example.shared.pdf.model.BasePdfContext;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * MonthReportContext
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/24 14:32
 */
@Getter
@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class MonthReportContext extends BasePdfContext {
  private String reportTitle;

  @Singular
  private List<ReportItem> items; // 表格数据

  @Override
  public String getTemplateName() {
    return "month-report";
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ReportItem {
    private String date;
    private String category;
    private String amount;
    private String remark;
  }
}
