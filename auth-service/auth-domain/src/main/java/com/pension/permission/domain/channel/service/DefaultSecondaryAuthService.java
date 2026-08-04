package com.pension.permission.domain.channel.service;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.contactinfo.Mobile;
import com.example.shared.domain.annotation.DomainService;
import com.example.shared.identifier.id.UserNo;

import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.shared.Channel;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import lombok.RequiredArgsConstructor;

@DomainService
@RequiredArgsConstructor
public final class DefaultSecondaryAuthService implements SecondaryAuthService {

  private final IdentityResolutionService identityResolutionService;

  @Override
  public EffectiveIdentity elevate(
    UserNo tellerAccountId, CredentialOwner credentialOwner,
    String proof, Mobile phoneNumber) {

    UserNo resolvedAgent = identityResolutionService
      .resolve(credentialOwner, AnnuityChannel.BANK_BRANCH, proof, phoneNumber)
      .orElseThrow(() -> new SecurityException("二次授权失败：凭证校验不通过，或无法定位到有效经办"));
    return new EffectiveIdentity(resolvedAgent, tellerAccountId, true);
  }
}
