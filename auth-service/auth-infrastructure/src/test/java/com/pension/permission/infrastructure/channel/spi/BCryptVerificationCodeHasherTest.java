package com.pension.permission.infrastructure.channel.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BCryptVerificationCodeHasher 测试")
class BCryptVerificationCodeHasherTest {

  private final BCryptVerificationCodeHasher hasher = new BCryptVerificationCodeHasher();

  @Nested
  @DisplayName("hash: 哈希验证码")
  class HashTest {

    @Test
    @DisplayName("应返回非空非null的哈希值")
    void shouldReturnNonNullOrEmptyHash() {
      var hashed = hasher.hash("123456");

      assertThat(hashed).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("相同明文每次哈希应产生不同结果（BCrypt加盐）")
    void shouldProduceDifferentHashForSameInput() {
      var hash1 = hasher.hash("123456");
      var hash2 = hasher.hash("123456");

      assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("哈希值应以 $2a$ 或 $2b$ 前缀开头（BCrypt格式）")
    void shouldStartWithBCryptPrefix() {
      var hashed = hasher.hash("123456");

      assertThat(hashed).startsWith("$2");
    }
  }

  @Nested
  @DisplayName("matches: 验证码匹配")
  class MatchesTest {

    @Test
    @DisplayName("正确验证码应匹配")
    void shouldMatchCorrectCode() {
      var hashed = hasher.hash("123456");

      assertThat(hasher.matches("123456", hashed)).isTrue();
    }

    @Test
    @DisplayName("错误验证码不应匹配")
    void shouldNotMatchWrongCode() {
      var hashed = hasher.hash("123456");

      assertThat(hasher.matches("654321", hashed)).isFalse();
    }

    @Test
    @DisplayName("null 明文应抛 IllegalArgumentException")
    void shouldThrowWhenRawCodeNull() {
      var hashed = hasher.hash("123456");

      assertThatThrownBy(() -> hasher.matches(null, hashed))
        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("不同明文的哈希不应互相匹配")
    void shouldNotMatchDifferentHash() {
      var hashOf123 = hasher.hash("123456");
      var hashOf456 = hasher.hash("456789");

      assertThat(hasher.matches("123456", hashOf456)).isFalse();
      assertThat(hasher.matches("456789", hashOf123)).isFalse();
    }
  }
}
