package com.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录响应.
 *
 * @param token      登录 token（需写入对应渠道的 token header 传回网关）
 * @param tokenName  token header 名称（satoken-internet / satoken-hq / satoken-branch）
 * @param accountId  账号 ID
 * @param channel    登录渠道
 */
public record LoginResponse(
  @NotBlank String token,
  @NotBlank String tokenName,
  @NotBlank String accountId,
  @NotBlank String channel
) {
}
