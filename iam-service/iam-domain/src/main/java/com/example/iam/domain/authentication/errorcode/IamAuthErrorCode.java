package com.example.iam.domain.authentication.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * iam-service 认证上下文错误码定义。
 *
 * <p>错误码区间 {@code SERVICE.IAM.0001-SERVICE.IAM.0071},遵循 {@code 08-错误码规范.md}:
 * <ul>
 *   <li>层级字符串格式:SERVICE.IAM.XXXX(业务服务 - iam-service - 认证)</li>
 *   <li>消息使用纯文本,禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * <p>码段内部分组:
 * <ul>
 *   <li>SERVICE.IAM.0001-0010:用户基础(存在性/状态/档案)</li>
 *   <li>SERVICE.IAM.0020-0027:凭据(密码/UKey/动态令牌)</li>
 *   <li>SERVICE.IAM.0040-0044:登录(失败次数/日志)</li>
 *   <li>SERVICE.IAM.0060-0071:二次授权(会话/策略/快照)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum IamAuthErrorCode implements ErrorDefinition {

  // ==================== 用户基础(SERVICE.IAM.0001-0010) ====================
  USER_NOT_FOUND("SERVICE.IAM.0001", "用户不存在"),
  USER_ALREADY_EXISTS("SERVICE.IAM.0002", "用户已存在"),
  USER_STATUS_INVALID("SERVICE.IAM.0003", "用户状态不允许此操作"),
  ACCOUNT_DISABLED("SERVICE.IAM.0004", "账号已禁用"),
  ACCOUNT_LOCKED("SERVICE.IAM.0005", "账号已锁定"),
  LOGIN_NAME_DUPLICATE("SERVICE.IAM.0006", "登录名重复"),
  CHANNEL_TYPE_INVALID("SERVICE.IAM.0007", "渠道类型无效"),
  USER_PROFILE_NOT_FOUND("SERVICE.IAM.0008", "用户档案不存在"),
  NOT_LOGGED_IN("SERVICE.IAM.0009", "当前请求未登录"),
  USER_PROFILE_INCOMPLETE("SERVICE.IAM.0010", "用户档案信息不完整"),

  // ==================== 凭据(SERVICE.IAM.0020-0027) ====================
  CREDENTIAL_INVALID("SERVICE.IAM.0020", "凭据无效"),
  CREDENTIAL_EXPIRED("SERVICE.IAM.0021", "凭据已过期"),
  CREDENTIAL_NOT_FOUND("SERVICE.IAM.0022", "凭据不存在"),
  CREDENTIAL_TYPE_NOT_SUPPORTED("SERVICE.IAM.0023", "不支持的凭据类型"),
  CREDENTIAL_VALIDATION_FAILED("SERVICE.IAM.0024", "凭据校验失败"),
  CREDENTIAL_REVOKED("SERVICE.IAM.0025", "凭据已撤销"),
  CREDENTIAL_TYPE_DUPLICATE("SERVICE.IAM.0026", "同类型凭据已存在"),
  CREDENTIAL_OWNER_MISMATCH("SERVICE.IAM.0027", "凭据归属不匹配"),

  // ==================== 登录(SERVICE.IAM.0040-0044) ====================
  LOGIN_FAIL_LIMIT_EXCEEDED("SERVICE.IAM.0040", "登录失败次数超限"),
  LOGIN_NAME_OR_PASSWORD_ERROR("SERVICE.IAM.0041", "登录名或密码错误"),
  CAPTCHA_INVALID("SERVICE.IAM.0042", "验证码无效"),
  LOGIN_LOG_NOT_FOUND("SERVICE.IAM.0043", "登录日志不存在"),
  LOGIN_FAILURE_RECORD_NOT_FOUND("SERVICE.IAM.0044", "登录失败记录不存在"),

  // ==================== 二次授权(SERVICE.IAM.0060-0071) ====================
  SECONDARY_AUTH_SESSION_NOT_FOUND("SERVICE.IAM.0060", "二次授权会话不存在"),
  SECONDARY_AUTH_SESSION_EXPIRED("SERVICE.IAM.0061", "二次授权会话已过期"),
  SECONDARY_AUTH_SESSION_COMPLETED("SERVICE.IAM.0062", "二次授权会话已完成"),
  SECONDARY_AUTH_SESSION_REVOKED("SERVICE.IAM.0063", "二次授权会话已撤销"),
  SECONDARY_AUTH_STRATEGY_NOT_SUPPORTED("SERVICE.IAM.0064", "不支持的二次授权策略"),
  SECONDARY_AUTH_REQUIRED("SERVICE.IAM.0065", "需要完成二次授权"),
  SECONDARY_AUTH_PENDING("SERVICE.IAM.0066", "已有待处理的二次授权请求"),
  SECONDARY_AUTH_APPROVER_MISMATCH("SERVICE.IAM.0067", "二次授权经办人不匹配"),
  SECONDARY_AUTH_PERMISSION_SNAPSHOT_MISSING("SERVICE.IAM.0068", "权限快照缺失"),
  NOT_BRANCH_USER_CANNOT_SWITCH_BACK("SERVICE.IAM.0069", "当前身份非柜员,无法切换回柜员"),
  SECONDARY_AUTH_APPROVER_INVALID("SERVICE.IAM.0070", "二次授权经办人无效"),
  SECONDARY_AUTH_PLAN_MISMATCH("SERVICE.IAM.0071", "二次授权计划不匹配"),
  ;

  private final String code;
  private final String message;

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
