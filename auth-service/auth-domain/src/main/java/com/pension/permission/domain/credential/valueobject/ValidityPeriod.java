package com.pension.permission.domain.credential.valueobject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 通用时间期限值对象 (Value Object)
 * <p>
 * 支持四种时间状态组合：
 * 1. start != null, end != null : 固定时间段
 * 2. start != null, end == null : 从某时刻起，永久有效
 * 3. start == null, end != null : 无起始限制，直到某时刻前有效
 * 4. start == null, end == null : 完全不受时间限制 (长期有效)
 *
 * @author panoshu
 * @since 2026/8/1
 */
public record ValidityPeriod(
  LocalDateTime start,
  LocalDateTime end
) {

  public ValidityPeriod {
    if (start != null && end != null && end.isBefore(start)) {
      throw new IllegalArgumentException("Validity end time cannot be before start time.");
    }
  }

  public static ValidityPeriod of(LocalDateTime start, Duration duration) {
    Objects.requireNonNull(start, "Start time is required for duration-based period.");
    Objects.requireNonNull(duration, "Duration is required.");
    return new ValidityPeriod(start, start.plus(duration));
  }

  public static ValidityPeriod infinite() {
    return new ValidityPeriod(null, null);
  }

  public static ValidityPeriod since(LocalDateTime start) {
    return new ValidityPeriod(start, null);
  }

  public static ValidityPeriod sinceNow() {
    return new ValidityPeriod(LocalDateTime.now(), null);
  }

  public static ValidityPeriod between(LocalDateTime start, LocalDateTime end) {
    return new ValidityPeriod(start, end);
  }

  public boolean isEffective(LocalDateTime now) {
    Objects.requireNonNull(now, "Current time is required.");
    boolean afterStart = (start == null) || !now.isBefore(start);
    boolean beforeEnd = (end == null) || now.isBefore(end);
    return afterStart && beforeEnd;
  }

  public boolean expired(LocalDateTime now) {
    Objects.requireNonNull(now, "Current time is required.");
    return end != null && !now.isBefore(end);
  }

  public ValidityPeriod renew(LocalDateTime from, Duration duration) {
    Objects.requireNonNull(from, "Renew 'from' time is required.");
    Objects.requireNonNull(duration, "Renew duration is required.");
    return new ValidityPeriod(from, from.plus(duration));
  }

  public ValidityPeriod extend(Duration duration) {
    Objects.requireNonNull(duration, "Extend duration is required.");
    if (end == null) {
      return this;
    }
    return new ValidityPeriod(start, end.plus(duration));
  }

  public boolean isInfinite() {
    return start == null && end == null;
  }
}
