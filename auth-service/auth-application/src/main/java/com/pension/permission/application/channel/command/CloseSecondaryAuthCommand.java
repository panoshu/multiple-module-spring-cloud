package com.pension.permission.application.channel.command;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

/**
 * 关闭二次授权会话命令.
 */
public record CloseSecondaryAuthCommand(
  SecondaryAuthSessionId sessionId,
  UserNo operator
) {}
