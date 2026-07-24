package com.example.shared.crypto;

import com.example.shared.crypto.errorcode.CryptoErrorCode;
import com.example.shared.exception.SystemException;
import com.tencent.kona.crypto.KonaCryptoProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Security;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sm4Encryptor 单元测试。
 *
 * @author trae
 * @since 1.0
 */
@DisplayName("SM4 加解密器测试")
class Sm4EncryptorTest {

  /** SM4 密钥必须为 16 字节，这里用 Base64 编码一个 16 字节全 1 密钥 */
  private static final String SECRET_KEY = Base64.getEncoder().encodeToString(
      new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1});

  private static Sm4Encryptor encryptor;

  @BeforeAll
  static void setUp() {
    // 注册 KonaCrypto Provider（测试环境无 Spring 容器，需手动注册）
    if (Security.getProvider("KonaCrypto") == null) {
      Security.addProvider(new KonaCryptoProvider());
    }
    encryptor = new Sm4Encryptor(SECRET_KEY);
  }

  @Test
  @DisplayName("加密后解密应还原原始明文")
  void encryptThenDecrypt_shouldRestoreOriginalText() {
    String plaintext = "13800138000";
    String ciphertext = encryptor.encrypt(plaintext);

    assertThat(ciphertext).isNotEqualTo(plaintext);
    assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plaintext);
  }

  @Test
  @DisplayName("同一明文多次加密应产生不同密文（随机 IV）")
  void encrypt_samePlaintext_shouldProduceDifferentCiphertext() {
    String plaintext = "张三";

    String cipher1 = encryptor.encrypt(plaintext);
    String cipher2 = encryptor.encrypt(plaintext);

    assertThat(cipher1).isNotEqualTo(cipher2);
    assertThat(encryptor.decrypt(cipher1)).isEqualTo(plaintext);
    assertThat(encryptor.decrypt(cipher2)).isEqualTo(plaintext);
  }

  @Test
  @DisplayName("应支持中文明文加解密")
  void encrypt_shouldSupportChineseText() {
    String plaintext = "身份证号：110101199001011234";
    String ciphertext = encryptor.encrypt(plaintext);

    assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plaintext);
  }

  @Test
  @DisplayName("应支持长文本加解密")
  void encrypt_shouldSupportLongText() {
    String plaintext = "a".repeat(10000);
    String ciphertext = encryptor.encrypt(plaintext);

    assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plaintext);
  }

  @Test
  @DisplayName("密钥为 null 应抛出 NPE")
  void constructor_nullKey_shouldThrowNpe() {
    assertThatThrownBy(() -> new Sm4Encryptor(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("密钥不能为空");
  }

  @Test
  @DisplayName("非法 Base64 密钥应抛出 SystemException")
  void constructor_invalidBase64Key_shouldThrowSystemException() {
    assertThatThrownBy(() -> new Sm4Encryptor("not-a-valid-base64!!!"))
        .isInstanceOf(SystemException.class)
        .satisfies(ex -> assertThat(((SystemException) ex).code())
            .isEqualTo(CryptoErrorCode.SECRET_KEY_INVALID.code()));
  }

  @Test
  @DisplayName("密钥长度非 16 字节应抛出 SystemException")
  void constructor_wrongKeyLength_shouldThrowSystemException() {
    // 8 字节密钥
    String shortKey = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

    assertThatThrownBy(() -> new Sm4Encryptor(shortKey))
        .isInstanceOf(SystemException.class)
        .satisfies(ex -> assertThat(((SystemException) ex).code())
            .isEqualTo(CryptoErrorCode.SECRET_KEY_INVALID.code()));
  }

  @Test
  @DisplayName("加密 null 明文应抛出 NPE")
  void encrypt_nullPlaintext_shouldThrowNpe() {
    assertThatThrownBy(() -> encryptor.encrypt(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("解密 null 密文应抛出 NPE")
  void decrypt_nullCiphertext_shouldThrowNpe() {
    assertThatThrownBy(() -> encryptor.decrypt(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("解密非法密文应抛出 SystemException")
  void decrypt_invalidCiphertext_shouldThrowSystemException() {
    assertThatThrownBy(() -> encryptor.decrypt("invalid-ciphertext"))
        .isInstanceOf(SystemException.class)
        .satisfies(ex -> assertThat(((SystemException) ex).code())
            .isEqualTo(CryptoErrorCode.DECRYPT_FAILED.code()));
  }
}
