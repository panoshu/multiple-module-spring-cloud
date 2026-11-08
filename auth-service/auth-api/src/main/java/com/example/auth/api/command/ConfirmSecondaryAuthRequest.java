package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 确认二次授权请求.
 *
 * @param sessionId 二次授权会话 ID
 * @param rawCode   验证码明文
 */
public record ConfirmSecondaryAuthRequest(
  @NotBlank String sessionId,
  @NotBlank String rawCode
) {
}
