package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求.
 *
 * @param credentialType 凭证类型: PASSWORD / U_KEY
 * @param ownerType      凭证持有者类型: USER / CUSTOMER / PLAN
 * @param ownerId        凭证持有者 ID（USER 时为 userNo，CUSTOMER 时为 customerNo，PLAN 时为 planNo）
 * @param channel        登录渠道: NETAPP / TELLER / BANK_BRANCH
 * @param proof          凭证证明（密码明文 / UKey 签名）
 * @param phoneNumber    经办手机号（客户/计划级凭证登录时必填，账号级凭证传 null）
 */
public record LoginRequest(
  @NotBlank String credentialType,
  @NotBlank String ownerType,
  @NotBlank String ownerId,
  @NotBlank String channel,
  @NotBlank String proof,
  String phoneNumber
) {
}
