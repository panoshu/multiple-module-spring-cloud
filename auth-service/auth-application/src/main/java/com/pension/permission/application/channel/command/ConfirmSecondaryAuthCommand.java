package com.pension.permission.application.channel.command;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

/**
 * 确认二次授权命令.
 */
public record ConfirmSecondaryAuthCommand(
  SecondaryAuthSessionId sessionId,
  String rawCode,
  UserNo operator
) {}
