package com.example.shared.crypto;

import com.example.shared.crypto.errorcode.CryptoErrorCode;
import com.example.shared.exception.SystemException;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * SM4 国密加解密器实现。
 *
 * <p>算法：SM4/CBC/PKCS7Padding（国密标准）
 * <br>Provider：KonaCrypto（腾讯 Kona 国密套件）
 * <br>IV：每次加密随机生成 16 字节 IV，拼接在密文前
 * <br>编码：URL-safe Base64（无填充）
 *
 * <p>密文格式：Base64( IV[16] || SM4加密数据 )
 *
 * <p>线程安全：是。无实例状态，密钥在构造时确定后不可变。
 *
 * @author trae
 * @since 1.0
 */
@Slf4j
public class Sm4Encryptor implements Encryptor {

  private static final String TRANSFORMATION = "SM4/CBC/PKCS7Padding";
  private static final String PROVIDER = "KonaCrypto";
  private static final String ALGORITHM = "SM4";
  private static final int IV_LENGTH = 16;

  private final byte[] secretKey;

  /**
   * 构造 SM4 加解密器。
   *
   * @param secretKey Base64 编码的 SM4 密钥（16 字节 = SM4 密钥长度）
   */
  public Sm4Encryptor(String secretKey) {
    Objects.requireNonNull(secretKey, "SM4 密钥不能为空");
    try {
      this.secretKey = Base64.getDecoder().decode(secretKey);
    } catch (IllegalArgumentException e) {
      throw new SystemException(CryptoErrorCode.SECRET_KEY_INVALID, e)
          .withLogDetail("密钥 Base64 解码失败");
    }
    if (this.secretKey.length != IV_LENGTH) {
      throw new SystemException(CryptoErrorCode.SECRET_KEY_INVALID)
          .withLogDetail("SM4 密钥长度必须为 16 字节，实际: " + this.secretKey.length);
    }
  }

  @Override
  public String encrypt(String plaintext) {
    Objects.requireNonNull(plaintext, "待加密明文不能为空");
    try {
      byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);

      byte[] iv = new byte[IV_LENGTH];
      new SecureRandom().nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey, ALGORITHM), new IvParameterSpec(iv));
      byte[] encrypted = cipher.doFinal(data);

      byte[] output = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, output, 0, iv.length);
      System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);

      return Base64.getUrlEncoder().withoutPadding().encodeToString(output);
    } catch (Exception e) {
      throw new SystemException(CryptoErrorCode.ENCRYPT_FAILED, e)
          .withLogDetail("SM4 加密失败: " + e.getMessage());
    }
  }

  @Override
  public String decrypt(String ciphertext) {
    Objects.requireNonNull(ciphertext, "待解密密文不能为空");
    try {
      byte[] input = Base64.getUrlDecoder().decode(ciphertext);
      byte[] iv = Arrays.copyOf(input, IV_LENGTH);
      byte[] encrypted = Arrays.copyOfRange(input, IV_LENGTH, input.length);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER);
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey, ALGORITHM), new IvParameterSpec(iv));
      byte[] decrypted = cipher.doFinal(encrypted);

      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new SystemException(CryptoErrorCode.DECRYPT_FAILED, e)
          .withLogDetail("SM4 解密失败: " + e.getMessage());
    }
  }
}
