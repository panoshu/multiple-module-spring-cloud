package com.example.auth.api.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 会话签名工具，用于网关签发 X-Account-Id/X-Session-Context 和业务服务验签。
 *
 * <p>使用 HMAC-SHA256 算法，仅依赖 JDK 内置类，无外部依赖。
 *
 * <p>安全特性：
 * <ul>
 *   <li>常量时间比较防时序攻击</li>
 *   <li>签名 + 过期双校验</li>
 *   <li>密钥缺失 fail-fast（抛 IllegalStateException）</li>
 * </ul>
 *
 * @author auth-api
 */
public final class SessionSignatureUtils {

  public static final String PAYLOAD_SEPARATOR = ":";
  public static final long DEFAULT_TTL_SECONDS = 300L;
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private SessionSignatureUtils() {
  }

  public static String sign(String payload, String secretKey) {
    if (payload == null || payload.isEmpty()) {
      throw new IllegalStateException("待签名内容不能为空");
    }
    if (secretKey == null || secretKey.isEmpty()) {
      throw new IllegalStateException("会话签名密钥未配置");
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("会话签名计算失败", e);
    }
  }

  public static String buildAccountIdPayload(String loginId, long expireAtEpochSecond) {
    return loginId + PAYLOAD_SEPARATOR + expireAtEpochSecond;
  }

  public static SignedPayload signAccountId(String loginId, String secretKey) {
    return signAccountId(loginId, secretKey, DEFAULT_TTL_SECONDS);
  }

  public static SignedPayload signAccountId(String loginId, String secretKey, long ttlSeconds) {
    long expireAt = Instant.now().getEpochSecond() + ttlSeconds;
    String payload = buildAccountIdPayload(loginId, expireAt);
    String signature = sign(payload, secretKey);
    return new SignedPayload(payload, signature, expireAt);
  }

  public static String signSessionContext(String sessionContextBase64,
                                          long expireAtEpochSecond,
                                          String secretKey) {
    String payload = sessionContextBase64 + PAYLOAD_SEPARATOR + expireAtEpochSecond;
    return sign(payload, secretKey);
  }

  public static boolean verify(String payload, String signature, String secretKey) {
    if (payload == null || signature == null || secretKey == null || secretKey.isEmpty()) {
      return false;
    }
    String expected = sign(payload, secretKey);
    return constantTimeEquals(expected, signature);
  }

  public static String verifyAccountId(String accountIdPayload,
                                       String signature,
                                       String secretKey) {
    if (!verify(accountIdPayload, signature, secretKey)) {
      return null;
    }
    int separatorIdx = accountIdPayload.lastIndexOf(PAYLOAD_SEPARATOR);
    if (separatorIdx <= 0) {
      return null;
    }
    String loginId = accountIdPayload.substring(0, separatorIdx);
    long expireAt;
    try {
      expireAt = Long.parseLong(accountIdPayload.substring(separatorIdx + 1));
    } catch (NumberFormatException e) {
      return null;
    }
    if (Instant.now().getEpochSecond() > expireAt) {
      return null;
    }
    return loginId;
  }

  public static boolean verifySessionContext(String sessionContextBase64,
                                             String signature,
                                             long expireAtEpochSecond,
                                             String secretKey) {
    if (Instant.now().getEpochSecond() > expireAtEpochSecond) {
      return false;
    }
    String payload = sessionContextBase64 + PAYLOAD_SEPARATOR + expireAtEpochSecond;
    return verify(payload, signature, secretKey);
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    if (a.length() != b.length()) {
      return false;
    }
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }

  public record SignedPayload(String payload, String signature, long expireAtEpochSecond) {
  }
}
