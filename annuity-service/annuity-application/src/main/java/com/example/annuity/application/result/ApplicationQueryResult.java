package com.example.annuity.application.result;

import java.time.LocalDateTime;

/**
 * 年金业务申请查询结果（应用层 Result 对象）
 * <p>
 * 由 {@code AnnuityAppService.getApplication} 返回，承载从 {@code BusinessApplication}
 * 聚合根提取的扁平化字段。Adapter 层通过 MapStruct Converter 转换为
 * {@code ApplicationResponse}。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
public record ApplicationQueryResult(
  String applicationId,
  String batchId,
  String formId,
  String businessType,
  String customerNo,
  String productNo,
  String planNo,
  String applicationStatus,
  String currentStep,
  String planType,
  Long initialContribution,
  Boolean hasForeignInvestment,
  String createdBy,
  LocalDateTime createdTime,
  String updatedBy,
  LocalDateTime updatedTime
) {
}
