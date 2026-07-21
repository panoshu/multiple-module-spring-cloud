package com.example.annuity.application.result;

/**
 * 年金表单上传结果（应用层 Result 对象）
 * <p>
 * 由 {@code AnnuityAppService.uploadForm} 返回，由 Adapter 层通过 MapStruct Converter
 * 转换为 {@code BatchStatusResponse}。
 * <p>
 * 当前 kernel 未公开 BusinessForm/BusinessBatch 的公开工厂方法，uploadForm 仅完成
 * 年金专属业务规则的校验和扩展字段构建，实际批次持久化需要 BFF 编排或后续 kernel 扩展。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
public record UploadFormResult(
    String batchId,
    String batchStatus,
    String formId,
    String formStatus,
    int totalApplications,
    int completedApplications,
    int failedApplications,
    String planType,
    Long initialContribution,
    Boolean hasForeignInvestment
) {

  /**
   * 状态常量：表示请求已通过校验但未持久化
   */
  public static final String STATUS_VALIDATED = "VALIDATED";

  /**
   * 创建"已校验"状态的预览结果
   */
  public static UploadFormResult validated(String planType, Long initialContribution,
                                            Boolean hasForeignInvestment) {
    return new UploadFormResult(
        null, STATUS_VALIDATED, null, STATUS_VALIDATED,
        0, 0, 0, planType, initialContribution, hasForeignInvestment
    );
  }
}
