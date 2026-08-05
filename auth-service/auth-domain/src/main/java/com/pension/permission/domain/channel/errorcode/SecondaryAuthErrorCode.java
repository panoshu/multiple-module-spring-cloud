package com.pension.permission.domain.channel.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 二次授权错误码.
 *
 * <p>遵循规则 08：SERVICE 域下 AUTH 模块缩写，范围 0101-0199.</p>
 */
@Getter
@AllArgsConstructor
public enum SecondaryAuthErrorCode implements ErrorDefinition {
  ACTIVE_SESSION_EXISTS("SERVICE.AUTH.0101", "柜员已有活跃的二次授权会话"),
  SESSION_NOT_FOUND("SERVICE.AUTH.0102", "二次授权会话不存在"),
  SESSION_EXPIRED("SERVICE.AUTH.0103", "二次授权会话已过期"),
  SESSION_NOT_PENDING("SERVICE.AUTH.0104", "二次授权会话不在待授权状态"),
  SESSION_NOT_AUTHORIZED("SERVICE.AUTH.0105", "二次授权会话不在已授权状态"),
  INVALID_VERIFICATION_CODE("SERVICE.AUTH.0106", "验证码错误"),
  VERIFICATION_CODE_EXPIRED("SERVICE.AUTH.0107", "验证码已过期"),
  VERIFICATION_CODE_EXHAUSTED("SERVICE.AUTH.0108", "验证码重试次数已耗尽"),
  SNAPSHOT_EXPIRED("SERVICE.AUTH.0109", "权限快照已过期"),
  SNAPSHOT_NOT_FOUND("SERVICE.AUTH.0110", "权限快照不存在"),
  APPROVER_NOT_FOUND("SERVICE.AUTH.0111", "经办人不存在"),
  APPROVER_NOT_ASSIGNED("SERVICE.AUTH.0112", "经办人在该计划上无生效的身份分配"),
  SMS_SEND_FAILED("SERVICE.AUTH.0113", "短信发送失败"),
  CHANNEL_NOT_SUPPORTED("SERVICE.AUTH.0114", "当前渠道不支持二次授权");

  private final String code;
  private final String message;
}
