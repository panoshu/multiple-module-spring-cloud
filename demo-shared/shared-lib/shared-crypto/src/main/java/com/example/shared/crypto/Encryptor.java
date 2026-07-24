package com.example.shared.crypto;

/**
 * 加解密器接口，定义通用的加密/解密契约。
 *
 * <p>实现类负责具体的加密算法（如 SM4、AES），调用方通过此接口解耦具体实现。
 *
 * @author trae
 * @since 1.0
 */
public interface Encryptor {

  /**
   * 加密明文字符串。
   *
   * @param plaintext 明文
   * @return 密文（Base64 编码）
   */
  String encrypt(String plaintext);

  /**
   * 解密密文字符串。
   *
   * @param ciphertext 密文（Base64 编码）
   * @return 明文
   */
  String decrypt(String ciphertext);
}
