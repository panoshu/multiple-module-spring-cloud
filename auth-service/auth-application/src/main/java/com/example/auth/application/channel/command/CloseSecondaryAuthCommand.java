package com.example.auth.application.channel.command;

import com.example.core.types.UserNo;
import com.example.auth.types.SecondaryAuthSessionId;

/**
 * 关闭二次授权会话命令.
 */
public record CloseSecondaryAuthCommand(
  SecondaryAuthSessionId sessionId,
  UserNo operator
) {
}
