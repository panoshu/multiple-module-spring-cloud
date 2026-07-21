package com.example.annuity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 年金表单上传请求 DTO
 * <p>
 * 由 BFF/前端调用，承载用户上传的原始表单元数据。Adapter 层通过 MapStruct Converter
 * 将其转换为 {@code UploadFormCommand}（应用层 CQE 对象），注入领域原语后交由
 * {@code AnnuityAppService} 编排处理。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
public record UploadFormRequest(
    @NotBlank String customerNo,
    @NotBlank String productNo,
    @NotBlank String planNo,
    @NotBlank String businessType,
    String operationModel,
    String accountManager,
    @NotBlank String operatorId,
    String operatorName,
    String channel,
    @NotBlank String fileName,
    @NotNull @Positive Long fileSize,
    String planType,
    Long initialContribution,
    Boolean hasForeignInvestment
) {
}
