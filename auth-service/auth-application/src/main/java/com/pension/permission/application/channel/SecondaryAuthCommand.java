package com.pension.permission.application.channel;


import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.types.SessionId;

/**
 * 网点柜员发起二次授权。credentialOwner可以是账号级(经办本人的凭证)，
 * 也可以是客户/计划级(企业统一UKey)——后者phoneNumber必填，用来定位具体经办。
 */
public record SecondaryAuthCommand(SessionId sessionId, CredentialOwner credentialOwner,
                                   String proof, Mobile phoneNumber, UserNo operator) {
}
