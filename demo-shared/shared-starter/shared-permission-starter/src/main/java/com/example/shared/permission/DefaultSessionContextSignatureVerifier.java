package com.example.shared.permission;

import com.example.auth.api.util.SessionSignatureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认会话上下文签名验证器。
 *
 * <p>使用 {@link SessionSignatureUtils} 进行 HMAC-SHA256 验签，
 * 共享密钥从 {@link PermissionProperties.SessionConfig#getSignatureKey()} 获取。
 *
 * <p>当 signatureKey 为空时（向后兼容），直接返回 true（不验签）。
 *
 * @author shared-permission-starter
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultSessionContextSignatureVerifier implements SessionContextSignatureVerifier {

  private final String signatureKey;

  @Override
  public boolean verify(String sessionContextBase64, String signature, long expireAtEpochSecond) {
    if (signatureKey == null || signatureKey.isEmpty()) {
      log.debug("[DefaultSessionContextSignatureVerifier] signatureKey 未配置,跳过验签");
      return true;
    }
    return SessionSignatureUtils.verifySessionContext(
      sessionContextBase64, signature, expireAtEpochSecond, signatureKey);
  }
}
