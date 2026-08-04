package com.example.shared.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * shared-domain 模块错误码定义。
 * <p>
 * 错误码区间 {@code SHARED.DOMAIN.0001-SHARED.DOMAIN.0099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式：SHARED.DOMAIN.XXXX（公共基础模块 - shared-domain）</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/6 19:03
 */
public enum SharedDomainErrorCode implements ErrorDefinition {

  ENTITY_NOT_FOUND("SHARED.DOMAIN.0001", "实体不存在"),
  INVALID_DATA("SHARED.DOMAIN.0002", "数据校验失败"),
  INVALID_OPERATION("SHARED.DOMAIN.0003", "操作无效"),

  ;

  final String code;
  final String message;

  SharedDomainErrorCode(String code, String message) {
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
