package com.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 发起二次授权响应.
 *
 * @param sessionId 二次授权会话 ID
 */
public record InitiateSecondaryAuthResponse(
  @NotBlank String sessionId
) {
}
