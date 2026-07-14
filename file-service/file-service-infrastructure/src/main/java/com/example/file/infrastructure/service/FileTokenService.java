package com.example.file.infrastructure.service;

import com.example.file.infrastructure.config.FileSecurityProperties;
import com.example.shared.file.types.dto.FileUrlReq;
import com.example.shared.file.types.dto.FileUrlResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileTokenService {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 128;
  private final FileSecurityProperties properties;
  private final ObjectMapper objectMapper;

  public FileUrlResp generatePresignedUrl(FileUrlReq req) {
    return this.generatePresignedUrl(
      req.fileId(), req.userId(), req.bizType(), req.action(), req.clientIp()
    );
  }

  /**
   * 生成完整的预签名 URL
   */
  public FileUrlResp generatePresignedUrl(String fileId, String userId, String bizType, FileUrlReq.FileAction action, String clientIp) {
    Instant timeoutInstant = Instant.now().plus(properties.defaultExpire());
    String targetIp = properties.checkIp() ? clientIp : null;

    // 1. 生成加密 Token
    TokenPayload payload = new TokenPayload(fileId, userId, bizType, action, timeoutInstant.toEpochMilli(), targetIp);
    String token = encryptToken(payload);

    // 2. 拼接完整 URL
    String baseUrl = StringUtils.trimTrailingCharacter(properties.publicUrl(), '/');
    String fullUrl;

    if (action == FileUrlReq.FileAction.DOWNLOAD) {
      // 下载链接: /secure/stream/{fileId}?token=xxx
      fullUrl = String.format("%s/secure/stream/%s?token=%s", baseUrl, fileId, token);
    } else {
      // 上传链接: /secure/upload?token=xxx
      fullUrl = String.format("%s/secure/upload?token=%s", baseUrl, token);
    }

    return new FileUrlResp(fullUrl, timeoutInstant.atZone(ZoneId.systemDefault()).toOffsetDateTime());
  }

  /**
   * 校验 Token 并返回 Payload
   */
  public TokenPayload verifyToken(String token, String fileId, FileUrlReq.FileAction requiredAction, String currentIp) {
    TokenPayload payload = decryptToken(token);

    // 1. 过期校验
    if (System.currentTimeMillis() > payload.expireAt()) {
      throw new SecurityException("Token expired");
    }

    // 2. 动作校验
    if (requiredAction != payload.action()) {
      throw new SecurityException("Token action mismatch");
    }

    // 3. ID 校验 (仅下载时校验 fileId，上传时 fileId 可能为 null 或占位符)
    if (fileId != null && !fileId.equals(payload.fileId())) {
      throw new SecurityException("Token fileId mismatch");
    }

    // 4. IP 校验
    if (properties.checkIp() && StringUtils.hasText(payload.clientIp())) {
      if (!payload.clientIp().equals(currentIp)) {
        log.warn("IP mismatch. Expected: {}, Actual: {}", payload.clientIp(), currentIp);
        throw new SecurityException("Invalid client IP");
      }
    }

    return payload;
  }

  private String encryptToken(TokenPayload payload) {
    try {
      String json = objectMapper.writeValueAsString(payload);
      byte[] iv = new byte[GCM_IV_LENGTH];
      new SecureRandom().nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(properties.secretKey().getBytes(), "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));

      byte[] cipherText = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
      ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
      byteBuffer.put(iv);
      byteBuffer.put(cipherText);

      return Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
    } catch (Exception e) {
      throw new RuntimeException("Token encryption failed", e);
    }
  }

  // --- 私有加密/解密方法 ---

  private TokenPayload decryptToken(String token) {
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(token);
      ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
      byte[] iv = new byte[GCM_IV_LENGTH];
      byteBuffer.get(iv);
      byte[] cipherText = new byte[byteBuffer.remaining()];
      byteBuffer.get(cipherText);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(properties.secretKey().getBytes(), "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));

      String json = new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
      return objectMapper.readValue(json, TokenPayload.class);
    } catch (Exception e) {
      throw new SecurityException("Invalid token");
    }
  }

  // 内部 Record，用于加密存储在 Token 中
  public record TokenPayload(String fileId, String userId, String bizType, FileUrlReq.FileAction action, long expireAt,
                             String clientIp) {
  }
}
