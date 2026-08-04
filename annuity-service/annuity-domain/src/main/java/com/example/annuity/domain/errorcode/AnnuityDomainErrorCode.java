package com.example.annuity.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * annuity-service 模块错误码定义。
 * <p>
 * 错误码区间 {@code SERVICE.ANNUITY.0001-SERVICE.ANNUITY.0099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式：SERVICE.ANNUITY.XXXX（业务服务模块 - annuity-service）</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author annuity-service
 * @since 2026/7/21
 */
public enum AnnuityDomainErrorCode implements ErrorDefinition {

  INVALID_CONTRIBUTION("SERVICE.ANNUITY.0001", "初始缴费金额无效"),
  UNSUPPORTED_PLAN_TYPE("SERVICE.ANNUITY.0002", "不支持的计划类型"),
  FOREIGN_INVESTMENT_REQUIRED("SERVICE.ANNUITY.0003", "外资业务需要额外审批"),
  EXTENSION_TYPE_MISMATCH("SERVICE.ANNUITY.0004", "业务扩展类型不匹配"),
  APPLICATION_NOT_FOUND("SERVICE.ANNUITY.0005", "年金申请单不存在"),
  BATCH_NOT_FOUND("SERVICE.ANNUITY.0006", "年金批次不存在"),
  FORM_NOT_FOUND("SERVICE.ANNUITY.0007", "年金表单不存在"),
  INVALID_EXTENSION_DATA("SERVICE.ANNUITY.0008", "年金扩展数据无效"),
  EMPLOYEE_VERIFICATION_FAILED("SERVICE.ANNUITY.0009", "员工明细核查失败"),
  EMPLOYEE_DETAIL_NOT_FOUND("SERVICE.ANNUITY.0010", "员工明细不存在"),
  BATCH_ALREADY_COMPLETED("SERVICE.ANNUITY.0011", "批次已完成,不可操作"),
  MATERIAL_CALCULATION_FAILED("SERVICE.ANNUITY.0012", "材料计算失败"),
  INVALID_EXTENSION_TYPE("SERVICE.ANNUITY.0013", "扩展字段类型不匹配"),
  FOREIGN_INVESTMENT_BLOCKED("SERVICE.ANNUITY.0014", "外资业务准入失败"),
  EMPLOYEE_BATCH_NOT_FOUND("SERVICE.ANNUITY.0015", "员工批次不存在"),
  ;

  final String code;
  final String message;

  AnnuityDomainErrorCode(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String getCode() {
    return this.code;
  }

  @Override
  public String getMessage() {
    return this.message;
  }
}
