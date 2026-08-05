package com.pension.permission.domain.channel.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VerificationCode 值对象测试")
class VerificationCodeTest {

  @Nested
  @DisplayName("创建验证码")
  class CreateTest {

    @Test
    @DisplayName("应当用明文和超时时间创建验证码")
    void should_create_when_valid_input() {
      LocalDateTime now = LocalDateTime.now();
      VerificationCode code = VerificationCode.of("123456", now, Duration.ofMinutes(5));
      assertThat(code.hashedCode()).isNotBlank();
      assertThat(code.sentAt()).isEqualTo(now);
      assertThat(code.expiresAt()).isEqualTo(now.plusMinutes(5));
      assertThat(code.remainingAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("明文验证码为空时应当抛异常")
    void should_throw_when_raw_code_null() {
      assertThatThrownBy(() -> VerificationCode.of(null, LocalDateTime.now(), Duration.ofMinutes(5)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("hashedCode");
    }

    @Test
    @DisplayName("超时时间为 null 时应当抛异常")
    void should_throw_when_timeout_null() {
      assertThatThrownBy(() -> VerificationCode.of("123456", LocalDateTime.now(), null))
        .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("过期判断")
  class IsExpiredTest {

    @Test
    @DisplayName("当前时间在过期时间之前应当未过期")
    void should_not_expired_when_before_expires_at() {
      LocalDateTime now = LocalDateTime.now();
      VerificationCode code = VerificationCode.of("123456", now, Duration.ofMinutes(5));
      assertThat(code.isExpired(now.plusMinutes(4))).isFalse();
    }

    @Test
    @DisplayName("当前时间等于过期时间应当已过期")
    void should_expired_when_equals_expires_at() {
      LocalDateTime now = LocalDateTime.now();
      VerificationCode code = VerificationCode.of("123456", now, Duration.ofMinutes(5));
      assertThat(code.isExpired(now.plusMinutes(5))).isTrue();
    }
  }

  @Nested
  @DisplayName("重试次数")
  class AttemptsTest {

    @Test
    @DisplayName("初始剩余次数应当为 3")
    void should_have_3_remaining_initially() {
      VerificationCode code = VerificationCode.of("123456", LocalDateTime.now(), Duration.ofMinutes(5));
      assertThat(code.remainingAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("失败一次后剩余次数应当减 1")
    void should_decrement_when_attempt_failed() {
      VerificationCode code = VerificationCode.of("123456", LocalDateTime.now(), Duration.ofMinutes(5));
      VerificationCode afterFail = code.onAttemptFailed();
      assertThat(afterFail.remainingAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("剩余次数为 0 时应当已耗尽")
    void should_exhausted_when_zero_remaining() {
      VerificationCode code = VerificationCode.of("123456", LocalDateTime.now(), Duration.ofMinutes(5));
      VerificationCode exhausted = code.onAttemptFailed().onAttemptFailed().onAttemptFailed();
      assertThat(exhausted.isExhausted()).isTrue();
    }

    @Test
    @DisplayName("4 参数 of 应当使用自定义 maxAttempts 作为初始剩余次数")
    void should_use_custom_max_attempts_when_provided() {
      LocalDateTime now = LocalDateTime.now();
      VerificationCode code = VerificationCode.of("123456", now, Duration.ofMinutes(5), 5);
      assertThat(code.remainingAttempts()).isEqualTo(5);
      assertThat(code.expiresAt()).isEqualTo(now.plusMinutes(5));
    }

    @Test
    @DisplayName("4 参数 of 当 maxAttempts 为负数时应当抛异常")
    void should_throw_when_max_attempts_negative() {
      assertThatThrownBy(() -> VerificationCode.of("123456", LocalDateTime.now(), Duration.ofMinutes(5), -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxAttempts");
    }

    @Test
    @DisplayName("自定义 maxAttempts 下失败 4 次后才耗尽")
    void should_exhaust_after_custom_max_attempts_failures() {
      VerificationCode code = VerificationCode.of("123456", LocalDateTime.now(), Duration.ofMinutes(5), 5);
      VerificationCode after4Fails = code.onAttemptFailed().onAttemptFailed().onAttemptFailed().onAttemptFailed();
      assertThat(after4Fails.remainingAttempts()).isEqualTo(1);
      assertThat(after4Fails.isExhausted()).isFalse();
      VerificationCode exhausted = after4Fails.onAttemptFailed();
      assertThat(exhausted.remainingAttempts()).isEqualTo(0);
      assertThat(exhausted.isExhausted()).isTrue();
    }
  }
}
