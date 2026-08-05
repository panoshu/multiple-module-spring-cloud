package com.pension.permission.domain.channel.service;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;

/**
 * @deprecated 旧的二次授权 SPI，已被 {@code SecondaryAuthAppService} +
 *     {@code SecondaryAuthSession} 聚合根的发起/确认两段式流程替代，将在后续版本移除。
 *     新代码请直接使用应用服务编排二次授权会话。
 */
@Deprecated
public interface SecondaryAuthService {
  EffectiveIdentity elevate(UserNo tellerAccountId, CredentialOwner credentialOwner,
                            String proof, Mobile phoneNumber);
}
