package com.pension.permission.domain.channel.valueobject;


import com.example.shared.identifier.id.UserNo;

/**
 * 会话里真正参与权限判定的身份。网点渠道二次授权后，identityAccountId是被授权的经办，
 * actingAccountId是柜员自己的账号——判定引擎只认identityAccountId，
 * actingAccountId只是用来做审计("这次操作实际是柜员X代经办Y做的")。
 */
public record EffectiveIdentity(
  UserNo identityAccountId,
  UserNo actingAccountId,
  boolean viaSecondaryAuth
) {
  public static EffectiveIdentity direct(UserNo accountId) {
    return new EffectiveIdentity(accountId, accountId, false);
  }
}
