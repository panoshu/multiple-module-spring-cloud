package com.example.gateway.crypto;

import com.example.shared.crypto.Encryptor;
import com.example.shared.json.action.FieldAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SM4 加解密策略实现：基于 {@link Encryptor} 提供加密/解密 {@link FieldAction}。
 *
 * <p>容错策略：加解密失败时返回 null（保留原值），不中断请求处理。
 * 空字符串不处理（加密无意义，解密会失败）。
 *
 * @author trae
 * @since 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class Sm4CryptoPolicy implements CryptoPolicy {

  private final Encryptor encryptor;

  @Override
  public FieldAction encryptAction() {
    return (fieldName, pathStack, value) -> {
      if (value.isEmpty()) {
        return null;
      }
      try {
        return encryptor.encrypt(value);
      } catch (Exception e) {
        log.warn("Encrypt field [{}] failed, keep original value", fieldName, e);
        return null;
      }
    };
  }

  @Override
  public FieldAction decryptAction() {
    return (fieldName, pathStack, value) -> {
      if (value.isEmpty()) {
        return null;
      }
      try {
        return encryptor.decrypt(value);
      } catch (Exception e) {
        log.warn("Decrypt field [{}] failed, keep original value", fieldName, e);
        return null;
      }
    };
  }
}