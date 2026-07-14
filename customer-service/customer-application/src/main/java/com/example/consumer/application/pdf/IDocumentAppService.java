package com.example.consumer.application.pdf;

import java.io.OutputStream;

/**
 * IDocumentAppService
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/24 14:51
 */
public interface IDocumentAppService {
  /**
   * 生成红头公文
   *
   * @param noticeId 公文ID
   */
  byte[] generateGovNotice(Long noticeId);

  /**
   * 生成客户风险告知函
   *
   * @param customerId 客户ID
   */
  byte[] generateRiskLetter(Long customerId);

  /**
   * 生成月度交易报表
   *
   * @param month 月份 (yyyy-MM)
   */
  byte[] generateMonthReport(String month);

  /**
   * 针对大文件的流式返回
   *
   * @param month        月份 (yyyy-MM)
   * @param outputStream 输出流
   */
  void generateReportStream(String month, OutputStream outputStream);
}
