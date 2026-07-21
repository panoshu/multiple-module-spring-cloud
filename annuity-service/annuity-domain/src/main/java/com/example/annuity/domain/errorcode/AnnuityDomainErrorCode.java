package com.example.annuity.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * 年金服务领域层错误码
 * <p>
 * 错误码区间：300001 ~ 300999（年金服务专用，与 kernel 200xxx、file 4xxxx 区分）
 *
 * @author annuity-service
 * @since 2026/7/21
 */
public enum AnnuityDomainErrorCode implements ErrorDefinition {

  INVALID_CONTRIBUTION("300001", "[初始缴费金额无效]{}"),
  UNSUPPORTED_PLAN_TYPE("300002", "[不支持的计划类型]{}"),
  FOREIGN_INVESTMENT_REQUIRED("300003", "[外资业务需要额外审批]{}"),
  EXTENSION_TYPE_MISMATCH("300004", "[业务扩展类型不匹配]{}"),
  APPLICATION_NOT_FOUND("300005", "[年金申请单不存在]{}"),
  BATCH_NOT_FOUND("300006", "[年金批次不存在]{}"),
  FORM_NOT_FOUND("300007", "[年金表单不存在]{}"),
  INVALID_EXTENSION_DATA("300008", "[年金扩展数据无效]{}"),
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
