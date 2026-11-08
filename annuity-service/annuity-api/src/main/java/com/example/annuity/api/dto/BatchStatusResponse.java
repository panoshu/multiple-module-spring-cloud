package com.example.annuity.api.dto;

import java.util.List;

/**
 * 年金业务批次状态响应 DTO
 * <p>
 * 描述批次及关联申请单的概要状态，供前端查询批次进度。
 * 通过 MapStruct Converter 从 {@code BusinessBatch} 聚合根及关联申请单列表聚合转换。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
public record BatchStatusResponse(
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
