package com.pension.permission.application.channel.command;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

/**
 * 撤销二次授权命令.
 */
public record RevokeSecondaryAuthCommand(
  SecondaryAuthSessionId sessionId,
  UserNo operator,
  String reason
) {}
