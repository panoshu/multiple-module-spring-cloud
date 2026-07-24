package com.example.core.domain.business.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * business-core-domain 模块错误码定义。
 * <p>
 * 错误码区间 {@code 20001-20099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>5 位纯数字，首位 2 表示业务核心模块，2-3 位 00 表示 business-core-domain</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 10:28
 */
public enum CoreDomainErrorCode implements ErrorDefinition {

  INVALID_STATUS("20001", "状态有误"),
  INVALID_DATA("20002", "数据有误"),
  INVALID_OPERATION("20003", "操作有误"),

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
