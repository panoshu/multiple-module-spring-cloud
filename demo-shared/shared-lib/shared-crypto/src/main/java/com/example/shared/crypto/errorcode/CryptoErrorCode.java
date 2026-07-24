package com.example.shared.crypto.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * shared-crypto 模块错误码定义。
 *
 * <p>码段分配：SHARED.CRYPTO.0001-SHARED.CRYPTO.0099（公共基础模块 - shared-crypto）
 * 参考 {@code 08-错误码规范.md} 第二节码段分配表。
 *
 * @author trae
 * @since 1.0
 */
public enum CryptoErrorCode implements ErrorDefinition {

  /** 加密失败 */
  ENCRYPT_FAILED("SHARED.CRYPTO.0001", "加密失败"),

  /** 解密失败 */
  DECRYPT_FAILED("SHARED.CRYPTO.0002", "解密失败"),

  /** 密钥未配置 */
  SECRET_KEY_NOT_CONFIGURED("SHARED.CRYPTO.0003", "密钥未配置"),

  /** 密钥格式非法 */
  SECRET_KEY_INVALID("SHARED.CRYPTO.0004", "密钥格式非法");

  private final String code;
  private final String message;

  CryptoErrorCode(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public String message() {
    return message;
  }
}
