package com.example.iam.domain.shared.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * iam-service 跨子域共用错误码定义。
 * <p>
 * 错误码区间 {@code SERVICE.IAM.0071-SERVICE.IAM.0099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式：SERVICE.IAM.XXXX（业务服务 - iam-service）</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/25
 */
public enum IamCommonErrorCode implements ErrorDefinition {

  EXTERNAL_SYSTEM_FAILURE("SERVICE.IAM.0071", "外部系统调用失败"),
  EXTERNAL_DATA_INVALID("SERVICE.IAM.0072", "外部系统返回数据无效"),
  UNKNOWN_ERROR("SERVICE.IAM.0099", "未知错误"),

  ;

  final String code;
  final String message;

  IamCommonErrorCode(String code, String message) {
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
