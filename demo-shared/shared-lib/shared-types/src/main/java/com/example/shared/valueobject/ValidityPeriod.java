package com.example.shared.valueobject;


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
 */
public record ValidityPeriod(
  LocalDateTime start,
  LocalDateTime end
) {

  /**
   * Record 的紧凑构造函数，用于统一校验不变性 (Invariants)
   */
  public ValidityPeriod {
    // 【改造点 1】移除了对 start 的非空强制校验，允许 start 为 null

    // 【改造点 2】仅当 start 和 end 都存在时，才校验 end 不能在 start 之前
    if (start != null && end != null && end.isBefore(start)) {
      throw new IllegalArgumentException("Validity end time cannot be before start time.");
    }
  }

  // ==========================================
  // 语义化静态工厂方法 (提升 DDD 表达力)
  // ==========================================

  /**
   * 创建固定时长的期限 (如：有效期 7 天)
   */
  public static ValidityPeriod of(LocalDateTime start, Duration duration) {
    Objects.requireNonNull(start, "Start time is required for duration-based period.");
    Objects.requireNonNull(duration, "Duration is required.");
    return new ValidityPeriod(start, start.plus(duration));
  }

  /**
   * 永久有效 / 长期有效 (无起始和结束限制，或从此刻起永久有效)
   */
  public static ValidityPeriod infinite() {
    return new ValidityPeriod(null, null);
  }

  public static ValidityPeriod since(LocalDateTime start) {
    return new ValidityPeriod(start, null);
  }

  public static ValidityPeriod sinceNow() {
    return new ValidityPeriod(LocalDateTime.now(), null);
  }

  /**
   * 指定明确的起止时间
   */
  public static ValidityPeriod between(LocalDateTime start, LocalDateTime end) {
    return new ValidityPeriod(start, end);
  }

  // ==========================================
  // 业务行为方法
  // ==========================================

  /**
   * 判断在指定时间点是否处于生效状态
   */
  public boolean isEffective(LocalDateTime now) {
    Objects.requireNonNull(now, "Current time is required.");

    // 如果 start 为 null，视为 -Infinity (永远在 now 之前或等于)
    boolean afterStart = (start == null) || !now.isBefore(start);
    // 如果 end 为 null，视为 +Infinity (永远在 now 之后)
    boolean beforeEnd = (end == null) || now.isBefore(end);

    return afterStart && beforeEnd;
  }

  /**
   * 判断在指定时间点是否已过期
   */
  public boolean expired(LocalDateTime now) {
    Objects.requireNonNull(now, "Current time is required.");
    // 只有当 end 存在，且 now >= end 时，才算过期
    return end != null && !now.isBefore(end);
  }

  /**
   * 从新的时间点重新计算有效期 (如：密码重置、Token 刷新)
   */
  public ValidityPeriod renew(LocalDateTime from, Duration duration) {
    Objects.requireNonNull(from, "Renew 'from' time is required.");
    Objects.requireNonNull(duration, "Renew duration is required.");
    return new ValidityPeriod(from, from.plus(duration));
  }

  /**
   * 在原有效期基础上延期 (如：UKey 延期一年)
   */
  public ValidityPeriod extend(Duration duration) {
    Objects.requireNonNull(duration, "Extend duration is required.");

    if (end == null) {
      // 已经是长期有效，无需/无法延期，返回原对象
      return this;
    }
    return new ValidityPeriod(start, end.plus(duration));
  }

  /**
   * 辅助方法：判断是否为长期有效
   */
  public boolean isInfinite() {
    return start == null && end == null;
  }
}
