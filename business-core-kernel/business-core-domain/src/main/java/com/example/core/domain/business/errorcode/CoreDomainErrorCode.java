package com.example.core.domain.business.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * business-core-domain 模块错误码定义。
 * <p>
 * 错误码区间 {@code CORE.DOMAIN.0001-CORE.DOMAIN.0099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式：CORE.DOMAIN.XXXX（业务核心模块 - business-core-domain）</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 10:28
 */
public enum CoreDomainErrorCode implements ErrorDefinition {

  INVALID_STATUS("CORE.DOMAIN.0001", "状态有误"),
  INVALID_DATA("CORE.DOMAIN.0002", "数据有误"),
  INVALID_OPERATION("CORE.DOMAIN.0003", "操作有误"),
  UNSUPPORTED_BUSINESS_TYPE("CORE.DOMAIN.0004", "不支持的业务类型"),
  PLAN_MISMATCH("CORE.DOMAIN.0005", "计划不一致"),
  PROXY_FORBIDDEN("CORE.DOMAIN.0006", "无代办权限"),
  SECONDARY_AUTH_REQUIRED("CORE.DOMAIN.0007", "需要二次授权"),

  ;

  final String code;
  final String message;

  CoreDomainErrorCode(String code, String message) {
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
