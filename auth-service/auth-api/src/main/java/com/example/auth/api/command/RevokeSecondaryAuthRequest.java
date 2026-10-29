package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 撤销二次授权请求.
 *
 * @param sessionId 二次授权会话 ID
 * @param reason    撤销原因
 */
public record RevokeSecondaryAuthRequest(
  @NotBlank String sessionId,
  String reason
) {
}
