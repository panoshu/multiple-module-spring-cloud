package com.example.auth.application.channel.command;

import com.example.core.types.UserNo;
import com.example.auth.types.SecondaryAuthSessionId;

/**
 * 重发验证码命令.
 */
public record ResendCodeCommand(
  SecondaryAuthSessionId sessionId,
  UserNo operator
) {
}
