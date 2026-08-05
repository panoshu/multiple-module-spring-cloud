package com.pension.permission.domain.channel.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 验证码值对象.
 *
 * <p>存储哈希后的验证码，不持有明文。明文仅在创建瞬间存在于应用层方法栈中。
 * 哈希校验通过 {@link com.pension.permission.domain.channel.spi.VerificationCodeHasher} 端口完成。</p>
 */
public record VerificationCode(
  String hashedCode,
  LocalDateTime sentAt,
  LocalDateTime expiresAt,
  int remainingAttempts
) implements ValueObject {

  public VerificationCode {
    Objects.requireNonNull(hashedCode, "hashedCode");
    Objects.requireNonNull(sentAt, "sentAt");
    Objects.requireNonNull(expiresAt, "expiresAt");
    if (expiresAt.isBefore(sentAt)) {
      throw new IllegalArgumentException("expiresAt must not be before sentAt");
    }
    if (remainingAttempts < 0) {
      throw new IllegalArgumentException("remainingAttempts must not be negative");
    }
  }

  /**
   * 创建验证码（应用层先哈希明文，再传入此方法）.
   *
   * @param hashedCode 已哈希的验证码字符串
   * @param sentAt 发送时间
   * @param timeout 超时时间
   * @return VerificationCode 实例
   */
  public static VerificationCode of(String hashedCode, LocalDateTime sentAt, Duration timeout) {
    Objects.requireNonNull(hashedCode, "hashedCode");
    Objects.requireNonNull(sentAt, "sentAt");
    Objects.requireNonNull(timeout, "timeout");
    return new VerificationCode(hashedCode, sentAt, sentAt.plus(timeout), 3);
  }

  /**
   * 校验是否已过期.
   */
  public boolean isExpired(LocalDateTime now) {
    return !now.isBefore(expiresAt);
  }

  /**
   * 校验重试次数是否已耗尽.
   */
  public boolean isExhausted() {
    return remainingAttempts <= 0;
  }

  /**
   * 记录一次校验失败，返回剩余次数减 1 的新实例（不可变）.
   */
  public VerificationCode onAttemptFailed() {
    return new VerificationCode(hashedCode, sentAt, expiresAt, Math.max(0, remainingAttempts - 1));
  }
}
