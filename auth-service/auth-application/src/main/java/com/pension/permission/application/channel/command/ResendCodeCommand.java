package com.pension.permission.application.channel.command;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

/**
 * 重发验证码命令.
 */
public record ResendCodeCommand(
  SecondaryAuthSessionId sessionId,
  UserNo operator
) {}
