package com.example.iam.adapter.security;

import com.example.shared.exception.SystemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BcryptPasswordEncryptorAdapter BCrypt 密码加密器测试。
 *
 * <p>覆盖核心可观察行为:
 * <ul>
 *   <li>encrypt:相同明文每次编码结果不同(BCrypt 随机盐);空入参抛 SystemException</li>
 *   <li>matches:正确密码/错误密码/null 密码/null 密文/异常密文的返回值</li>
 *   <li>encrypt 后能 matches(端到端闭环)</li>
 * </ul>
 *
 * <p>不使用 mock,直接验证真实 BCrypt 行为(密码加密是纯函数式计算,无需隔离)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BcryptPasswordEncryptorAdapter BCrypt 密码加密器")
class BcryptPasswordEncryptorAdapterTest {

  private BcryptPasswordEncryptorAdapter encryptor;

  @BeforeEach
  void setUp() {
    encryptor = new BcryptPasswordEncryptorAdapter();
  }

  @Nested
  @DisplayName("encrypt")
  class Encrypt {

    @Test
    @DisplayName("相同明文每次编码结果不同(BCrypt 随机盐)")
    void samePlainPassword_producesDifferentHashes() {
      String plain = "MySecret@123";

      String hash1 = encryptor.encrypt(plain);
      String hash2 = encryptor.encrypt(plain);

      assertThat(hash1).isNotEqualTo(hash2);
      assertThat(hash1).startsWith("$2a$");
      assertThat(hash2).startsWith("$2a$");
    }

    @Test
    @DisplayName("null 密码抛 SystemException")
    void nullPassword_throwsSystemException() {
      assertThatThrownBy(() -> encryptor.encrypt(null))
          .isInstanceOf(SystemException.class);
    }

    @Test
    @DisplayName("空字符串密码抛 SystemException")
    void emptyPassword_throwsSystemException() {
      assertThatThrownBy(() -> encryptor.encrypt(""))
          .isInstanceOf(SystemException.class);
    }
  }

  @Nested
  @DisplayName("matches")
  class Matches {

    @Test
    @DisplayName("正确密码返回 true")
    void correctPassword_returnsTrue() {
      String hash = encryptor.encrypt("MySecret@123");

      assertThat(encryptor.matches("MySecret@123", hash)).isTrue();
    }

    @Test
    @DisplayName("错误密码返回 false")
    void wrongPassword_returnsFalse() {
      String hash = encryptor.encrypt("MySecret@123");

      assertThat(encryptor.matches("WrongPassword", hash)).isFalse();
    }

    @Test
    @DisplayName("null 明文密码返回 false")
    void nullPlainPassword_returnsFalse() {
      String hash = encryptor.encrypt("MySecret@123");

      assertThat(encryptor.matches(null, hash)).isFalse();
    }

    @Test
    @DisplayName("空字符串明文密码返回 false")
    void emptyPlainPassword_returnsFalse() {
      String hash = encryptor.encrypt("MySecret@123");

      assertThat(encryptor.matches("", hash)).isFalse();
    }

    @Test
    @DisplayName("null 密文返回 false")
    void nullEncryptedPassword_returnsFalse() {
      assertThat(encryptor.matches("MySecret@123", null)).isFalse();
    }

    @Test
    @DisplayName("空字符串密文返回 false")
    void emptyEncryptedPassword_returnsFalse() {
      assertThat(encryptor.matches("MySecret@123", "")).isFalse();
    }

    @Test
    @DisplayName("格式异常密文返回 false(不抛错)")
    void malformedEncryptedPassword_returnsFalse() {
      assertThat(encryptor.matches("MySecret@123", "not-a-bcrypt-hash"))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("端到端闭环")
  class EndToEnd {

    @Test
    @DisplayName("encrypt 后 matches 同一明文返回 true")
    void encryptThenMatches_returnsTrue() {
      String plain = "Passw0rd!";

      String hash = encryptor.encrypt(plain);

      assertThat(encryptor.matches(plain, hash)).isTrue();
    }

    @Test
    @DisplayName("两次 encrypt 同一明文,任一 hash 都能 matches")
    void twoHashesBothMatchSamePlain() {
      String plain = "Passw0rd!";

      String hash1 = encryptor.encrypt(plain);
      String hash2 = encryptor.encrypt(plain);

      assertThat(encryptor.matches(plain, hash1)).isTrue();
      assertThat(encryptor.matches(plain, hash2)).isTrue();
    }
  }
}
