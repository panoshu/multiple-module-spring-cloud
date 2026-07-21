package com.example.annuity.application.result;

import java.util.List;

/**
 * 年金业务批次状态查询结果（应用层 Result 对象）
 * <p>
 * 由 {@code AnnuityAppService.getBatchStatus} 返回，承载批次及关联申请单的概要信息。
 * Adapter 层通过 MapStruct Converter 转换为 {@code BatchStatusResponse}。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
public record BatchQueryResult(
    String batchId,
    String batchStatus,
    String formId,
    String formStatus,
    int totalApplications,
    int completedApplications,
    int failedApplications,
    List<ApplicationSummary> applications
) {

  /**
   * 批次内单个申请单的摘要信息
   */
  public record ApplicationSummary(
      String applicationId,
      String businessType,
      String applicationStatus,
      String currentStep
  ) {
  }
}
