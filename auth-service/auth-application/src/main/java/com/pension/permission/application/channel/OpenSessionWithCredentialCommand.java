package com.pension.permission.application.channel;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.contactinfo.Mobile;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;

/**
 * 用凭证登录(而不是已经确定AccountId直接建会话)。适用于：
 * - 账号级凭证(密码/个人UKey登录)：phoneNumber传null
 * - 客户/计划级凭证(企业UKey+经办手机号登录，网上渠道经办的典型登录方式)：phoneNumber必填
 */
public record OpenSessionWithCredentialCommand(CredentialOwner credentialOwner, AnnuityChannel channel,
                                               String proof, Mobile phoneNumber) {
}
