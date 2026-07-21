package com.example.annuity.api.dto;

/**
 * 年金业务申请响应 DTO
 * <p>
 * 描述单个 {@code BusinessApplication} 的关键状态字段，供前端查询申请单详情。
 * 通过 MapStruct Converter 从领域聚合根转换为该 DTO，禁止在 Adapter 中直接构造。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
public record ApplicationResponse(
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
    String createdTime,
    String updatedBy,
    String updatedTime
) {
}
