package com.example.file.infrastructure.storage;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileTokenGateway;
import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.shared.crypto.Sm4Encryptor;
import com.example.shared.exception.SystemException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件令牌网关实现，基于 SM4 加解密。
 *
 * <p>加解密逻辑委托给 {@link Sm4Encryptor}（shared-crypto 模块），
 * 本类只负责 FileTokenPayload 与 JSON 之间的序列化/反序列化。
 *
 * @author trae
 * @since 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class KonaFileTokenGateway implements FileTokenGateway {

  private final ObjectMapper objectMapper;
  private final Sm4Encryptor encryptor;

  @Override
  public String encrypt(FileTokenPayload payload) {
    try {
      String json = objectMapper.writeValueAsString(payload);
      return encryptor.encrypt(json);
    } catch (Exception e) {
      throw new SystemException(FileErrorCodes.FILE_TOKEN_SECRET_NOT_CONFIGURED, e)
        .withLogDetail("加密失败: " + e.getMessage());
    }
  }

  @Override
  public FileTokenPayload decrypt(String token) {
    try {
      String json = encryptor.decrypt(token);
      return objectMapper.readValue(json, FileTokenPayload.class);
    } catch (Exception e) {
      throw new SystemException(FileErrorCodes.FILE_TOKEN_INVALID, e)
        .withLogDetail("解密失败: " + e.getMessage());
    }
  }
}
