package com.example.auth.application.channel;

import com.example.core.types.Channel;
import com.example.core.types.contact.Mobile;
import com.example.auth.domain.credential.valueobject.owner.CredentialOwner;

/**
 * 用凭证登录(而不是已经确定AccountId直接建会话)。适用于：
 * - 账号级凭证(密码/个人UKey登录)：phoneNumber传null
 * - 客户/计划级凭证(企业UKey+经办手机号登录，网上渠道经办的典型登录方式)：phoneNumber必填
 */
public record OpenSessionWithCredentialCommand(CredentialOwner credentialOwner, Channel channel,
                                               String proof, Mobile phoneNumber) {
}
