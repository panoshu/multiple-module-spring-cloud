package com.pension.permission.domain.channel.service;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;

/**
 * @deprecated 已被 {@link SecondaryAuthStrategy} 替代，将在后续版本移除。
 *     新代码请使用 SecondaryAuthStrategy SPI。
 */
@Deprecated
public interface SecondaryAuthService {
  EffectiveIdentity elevate(UserNo tellerAccountId, CredentialOwner credentialOwner,
                            String proof, Mobile phoneNumber);
}
