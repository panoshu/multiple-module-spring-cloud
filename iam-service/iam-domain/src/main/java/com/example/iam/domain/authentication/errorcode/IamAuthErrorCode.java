package com.example.iam.domain.authentication.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * iam-service 认证上下文错误码定义。
 * <p>
 * 错误码区间 {@code SERVICE.IAM.0001-SERVICE.IAM.0015}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式：SERVICE.IAM.XXXX（业务服务 - iam-service - 认证）</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/25
 */
public enum IamAuthErrorCode implements ErrorDefinition {

  USER_NOT_FOUND("SERVICE.IAM.0001", "用户不存在"),
  CREDENTIAL_INVALID("SERVICE.IAM.0002", "凭据无效"),
  CREDENTIAL_EXPIRED("SERVICE.IAM.0003", "凭据已过期"),
  ACCOUNT_DISABLED("SERVICE.IAM.0004", "账号已禁用"),
  ACCOUNT_LOCKED("SERVICE.IAM.0005", "账号已锁定"),
  LOGIN_FAIL_LIMIT_EXCEEDED("SERVICE.IAM.0006", "登录失败次数超限"),
  SECONDARY_AUTH_SESSION_NOT_FOUND("SERVICE.IAM.0011", "二次授权会话不存在"),
  SECONDARY_AUTH_SESSION_EXPIRED("SERVICE.IAM.0012", "二次授权会话已过期"),
  SECONDARY_AUTH_SESSION_COMPLETED("SERVICE.IAM.0013", "二次授权会话已完成"),
  SECONDARY_AUTH_STRATEGY_NOT_SUPPORTED("SERVICE.IAM.0014", "不支持的二次授权策略"),
  NOT_BRANCH_USER_CANNOT_SWITCH_BACK("SERVICE.IAM.0015", "当前身份非柜员，无法切换回柜员"),

  ;

  final String code;
  final String message;

  IamAuthErrorCode(String code, String message) {
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
