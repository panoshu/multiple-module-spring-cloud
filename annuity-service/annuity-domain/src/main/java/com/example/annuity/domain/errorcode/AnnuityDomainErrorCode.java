package com.example.annuity.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * annuity-service 模块错误码定义。
 * <p>
 * 错误码区间 {@code 33001-33099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>5 位纯数字，首位 3 表示业务服务模块，2-3 位 30 表示 annuity-service</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author annuity-service
 * @since 2026/7/21
 */
public enum AnnuityDomainErrorCode implements ErrorDefinition {

  INVALID_CONTRIBUTION("33001", "初始缴费金额无效"),
  UNSUPPORTED_PLAN_TYPE("33002", "不支持的计划类型"),
  FOREIGN_INVESTMENT_REQUIRED("33003", "外资业务需要额外审批"),
  EXTENSION_TYPE_MISMATCH("33004", "业务扩展类型不匹配"),
  APPLICATION_NOT_FOUND("33005", "年金申请单不存在"),
  BATCH_NOT_FOUND("33006", "年金批次不存在"),
  FORM_NOT_FOUND("33007", "年金表单不存在"),
  INVALID_EXTENSION_DATA("33008", "年金扩展数据无效"),
  EMPLOYEE_VERIFICATION_FAILED("33009", "员工明细核查失败"),
  EMPLOYEE_DETAIL_NOT_FOUND("33010", "员工明细不存在"),
  BATCH_ALREADY_COMPLETED("33011", "批次已完成,不可操作"),
  MATERIAL_CALCULATION_FAILED("33012", "材料计算失败"),
  INVALID_EXTENSION_TYPE("33013", "扩展字段类型不匹配"),
  FOREIGN_INVESTMENT_BLOCKED("33014", "外资业务准入失败"),
  EMPLOYEE_BATCH_NOT_FOUND("33015", "员工批次不存在"),
  ;

  final String code;
  final String message;

  AnnuityDomainErrorCode(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String code() {
    return this.code;
  }

  @Override
  public String message() {
    return this.message;
  }
}
