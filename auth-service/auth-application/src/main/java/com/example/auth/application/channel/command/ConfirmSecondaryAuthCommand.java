package com.example.auth.application.channel.command;

import com.example.core.types.UserNo;
import com.example.auth.domain.channel.valueobject.PermissionSnapshot;
import com.example.auth.types.SecondaryAuthSessionId;

/**
 * 确认二次授权命令.
 *
 * @param sessionId 会话ID
 * @param rawCode   验证码明文
 * @param snapshot  权限快照（由 PermissionResolver 预先解析后传入）
 * @param operator  操作人
 */
public record ConfirmSecondaryAuthCommand(
  SecondaryAuthSessionId sessionId,
  String rawCode,
  PermissionSnapshot snapshot,
  UserNo operator
) {
}
