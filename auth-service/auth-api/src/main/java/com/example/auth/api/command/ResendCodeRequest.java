package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 重发验证码请求.
 *
 * @param sessionId 二次授权会话 ID
 */
public record ResendCodeRequest(
  @NotBlank String sessionId
) {
}
