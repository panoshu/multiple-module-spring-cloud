package com.example.iam.domain.authentication.aggregate.entity;

import com.example.iam.types.LoginFailureRecordId;
import com.example.shared.domain.aggregate.entity.Entity;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 登录失败记录实体(LoginLog 聚合内实体)。
 *
 * <p>当登录失败时,由 {@code LoginLog.createFailure} 工厂方法或 {@code LoginLog.addFailureRecord}
 * 创建,记录失败原因与发生时间。一次登录尝试可关联多条失败记录(如密码错误 + IP 黑名单)。
 *
 * <p>该实体不独立持久化,通过 {@code t_iam_login_failure_record} 表与 LoginLog 一同加载/保存。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public class LoginFailureRecord extends Entity<LoginFailureRecordId> {

  private final String reason;
  private final String detail;
  private final LocalDateTime failureTime;

  /**
   * 业务创建构造器(由 LoginLog 聚合根调用)。
   *
   * @param id          失败记录 ID
   * @param reason      失败原因代码(如 {@code WRONG_PASSWORD}/{@code USER_NOT_FOUND},非空)
   * @param detail      人类可读详情(可空)
   * @param failureTime 失败时间
   */
  public LoginFailureRecord(LoginFailureRecordId id, String reason, String detail, LocalDateTime failureTime) {
    super(id, UserNo.of("U-SYSTEM"));
    this.reason = Objects.requireNonNull(reason, "reason cannot be null");
    if (reason.isBlank()) {
      throw new IllegalArgumentException("reason cannot be blank");
    }
    this.detail = detail;
    this.failureTime = Objects.requireNonNull(failureTime, "failureTime cannot be null");
    this.validateInvariants();
  }

  /**
   * 数据库重建构造器。
   */
  public LoginFailureRecord(LoginFailureRecordId id, String reason, String detail, LocalDateTime failureTime,
                             UserNo createdBy, UserNo updatedBy,
                             LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.reason = reason;
    this.detail = detail;
    this.failureTime = failureTime;
    this.validateInvariants();
  }

  public String reason() { return reason; }
  public String detail() { return detail; }
  public LocalDateTime failureTime() { return failureTime; }

  @Override
  protected void validateInvariants() {
    if (reason == null || reason.isBlank()) {
      throw new IllegalStateException("LoginFailureRecord.reason cannot be null or blank");
    }
    if (failureTime == null) {
      throw new IllegalStateException("LoginFailureRecord.failureTime cannot be null");
    }
  }
}
