package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 关闭二次授权会话请求（柜员登出）.
 *
 * @param sessionId 二次授权会话 ID
 */
public record CloseSecondaryAuthRequest(
  @NotBlank String sessionId
) {
}
