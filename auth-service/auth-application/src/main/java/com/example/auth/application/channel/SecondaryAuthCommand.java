package com.example.auth.application.channel;


import com.example.core.types.contact.Mobile;
import com.example.core.types.UserNo;
import com.example.auth.domain.credential.valueobject.owner.CredentialOwner;
import com.example.auth.types.SessionId;

/**
 * 网点柜员发起二次授权。credentialOwner可以是账号级(经办本人的凭证)，
 * 也可以是客户/计划级(企业统一UKey)——后者phoneNumber必填，用来定位具体经办。
 */
public record SecondaryAuthCommand(SessionId sessionId, CredentialOwner credentialOwner,
                                   String proof, Mobile phoneNumber, UserNo operator) {
}
