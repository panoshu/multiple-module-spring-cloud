package com.example.auth.application.channel.command;

import com.example.core.types.UserNo;
import com.example.auth.types.SecondaryAuthSessionId;

/**
 * 撤销二次授权命令.
 */
public record RevokeSecondaryAuthCommand(
  SecondaryAuthSessionId sessionId,
  UserNo operator,
  String reason
) {
}
