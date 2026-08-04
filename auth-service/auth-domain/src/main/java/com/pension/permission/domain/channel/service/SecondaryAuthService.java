package com.pension.permission.domain.channel.service;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;

/**
 * 网点二次授权：本质是"身份提升"——柜员用凭证(账号级的经办本人凭证，
 * 或客户/计划级的企业UKey+经办手机号)换取以经办身份操作的会话。
 * 具体的凭证校验/身份定位全部委托给IdentityResolutionService，这里只负责
 * "把解析出来的AccountId包装成EffectiveIdentity(带上柜员本人作为acting账号)"。
 */
public interface SecondaryAuthService {
  EffectiveIdentity elevate(UserNo tellerAccountId, CredentialOwner credentialOwner,
                            String proof, Mobile phoneNumber);
}
