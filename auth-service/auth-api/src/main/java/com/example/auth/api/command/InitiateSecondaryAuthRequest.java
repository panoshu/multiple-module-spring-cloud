package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 发起二次授权请求.
 *
 * @param tellerAccountId   柜员账号 ID
 * @param ownerType         凭证持有者类型: USER / CUSTOMER / PLAN
 * @param ownerId           凭证持有者 ID
 * @param approverAccountId 经办人账号 ID
 * @param approverMobile    经办人手机号
 * @param planId            目标计划 ID（可空）
 */
public record InitiateSecondaryAuthRequest(
  @NotBlank String tellerAccountId,
  @NotBlank String ownerType,
  @NotBlank String ownerId,
  @NotBlank String approverAccountId,
  @NotBlank String approverMobile,
  String planId
) {
}
