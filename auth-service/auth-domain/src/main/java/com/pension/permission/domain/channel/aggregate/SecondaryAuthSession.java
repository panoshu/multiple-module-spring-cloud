package com.pension.permission.domain.channel.aggregate;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.contactinfo.Mobile;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.errorcode.SecondaryAuthErrorCode;
import com.pension.permission.domain.channel.event.SecondaryAuthCompleted;
import com.pension.permission.domain.channel.event.SecondaryAuthInitiated;
import com.pension.permission.domain.channel.event.SecondaryAuthRejected;
import com.pension.permission.domain.channel.spi.VerificationCodeHasher;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.VerificationCode;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 二次授权会话聚合根.
 *
 * <p>支持短信验证码两段式授权：
 * <ol>
 *   <li>柜员发起 → PENDING，生成验证码，发短信</li>
 *   <li>柜员输入验证码 → AUTHORIZED，冻结权限快照</li>
 *   <li>经办人撤销 / 紧急收权 → REVOKED</li>
 *   <li>柜员登出 / 会话过期 → CLOSED</li>
 *   <li>待授权超时 / 快照 TTL 过期 → EXPIRED</li>
 *   <li>验证码重试耗尽 → REJECTED</li>
 * </ol>
 * </p>
 */
public class SecondaryAuthSession extends AggregateRoot<SecondaryAuthSessionId> {

  private final UserNo tellerAccountId;
  private UserNo approverAccountId;
  private final CredentialOwner credentialOwner;
  private final Mobile approverMobile;
  private final PlanNo planId;
  private VerificationCode verificationCode;
  private EffectiveIdentity effectiveIdentity;
  private PermissionSnapshot permissionSnapshot;
  private SecondaryAuthStatus status;
  private final LocalDateTime initiatedAt;
  private final LocalDateTime pendingExpiresAt;
  private LocalDateTime authorizedAt;
  private final LocalDateTime expiresAt;
  private String revokeReason;

  private SecondaryAuthSession(
    SecondaryAuthSessionId id, UserNo creator,
    UserNo tellerAccountId, UserNo approverAccountId,
    CredentialOwner credentialOwner, Mobile approverMobile,
    PlanNo planId,
    VerificationCode verificationCode,
    LocalDateTime initiatedAt, LocalDateTime pendingExpiresAt, LocalDateTime expiresAt,
    SecondaryAuthStatus status
  ) {
    super(id, creator);
    this.tellerAccountId = tellerAccountId;
    this.approverAccountId = approverAccountId;
    this.credentialOwner = credentialOwner;
    this.approverMobile = approverMobile;
    this.planId = planId;
    this.verificationCode = verificationCode;
    this.initiatedAt = initiatedAt;
    this.pendingExpiresAt = pendingExpiresAt;
    this.expiresAt = expiresAt;
    this.status = status;
    validateInvariants();
    registerDomainEvent(SecondaryAuthInitiated.of(
      id, tellerAccountId, approverAccountId, creator));
  }

  private SecondaryAuthSession(
    SecondaryAuthSessionId id, UserNo createdBy, UserNo updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
    UserNo tellerAccountId, UserNo approverAccountId,
    CredentialOwner credentialOwner, Mobile approverMobile,
    PlanNo planId,
    VerificationCode verificationCode,
    EffectiveIdentity effectiveIdentity,
    PermissionSnapshot permissionSnapshot,
    SecondaryAuthStatus status,
    LocalDateTime initiatedAt, LocalDateTime pendingExpiresAt, LocalDateTime authorizedAt,
    LocalDateTime expiresAt, String revokeReason
  ) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.tellerAccountId = tellerAccountId;
    this.approverAccountId = approverAccountId;
    this.credentialOwner = credentialOwner;
    this.approverMobile = approverMobile;
    this.planId = planId;
    this.verificationCode = verificationCode;
    this.effectiveIdentity = effectiveIdentity;
    this.permissionSnapshot = permissionSnapshot;
    this.status = status;
    this.initiatedAt = initiatedAt;
    this.pendingExpiresAt = pendingExpiresAt;
    this.authorizedAt = authorizedAt;
    this.expiresAt = expiresAt;
    this.revokeReason = revokeReason;
    validateInvariants();
  }

  /**
   * 柜员发起二次授权（PENDING）.
   *
   * @param id 会话 ID
   * @param tellerAccountId 柜员账号
   * @param credentialOwner 发起时使用的凭证持有者
   * @param approverAccountId 经办人账号
   * @param approverMobile 经办人手机号
   * @param planId 目标计划（可为 null，用于非计划场景）
   * @param verificationCode 验证码值对象
   * @param pendingTimeout 待授权超时时间（默认 5 分钟）
   * @param sessionTimeout 会话过期时间（默认 2 小时）
   * @param operator 操作人（柜员）
   * @return SecondaryAuthSession 实例
   */
  public static SecondaryAuthSession initiate(
    SecondaryAuthSessionId id,
    UserNo tellerAccountId,
    CredentialOwner credentialOwner,
    UserNo approverAccountId,
    Mobile approverMobile,
    PlanNo planId,
    VerificationCode verificationCode,
    Duration pendingTimeout,
    Duration sessionTimeout,
    UserNo operator
  ) {
    Objects.requireNonNull(tellerAccountId, "tellerAccountId");
    Objects.requireNonNull(credentialOwner, "credentialOwner");
    Objects.requireNonNull(approverAccountId, "approverAccountId");
    Objects.requireNonNull(approverMobile, "approverMobile");
    Objects.requireNonNull(verificationCode, "verificationCode");
    Objects.requireNonNull(pendingTimeout, "pendingTimeout");
    Objects.requireNonNull(sessionTimeout, "sessionTimeout");
    Objects.requireNonNull(operator, "operator");
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime pendingExpiresAt = now.plus(pendingTimeout);
    return new SecondaryAuthSession(
      id, operator,
      tellerAccountId, approverAccountId,
      credentialOwner, approverMobile,
      planId, verificationCode,
      now, pendingExpiresAt, now.plus(sessionTimeout),
      SecondaryAuthStatus.PENDING);
  }

  /**
   * 柜员输入验证码确认（PENDING → AUTHORIZED）.
   *
   * <p>验证码校验通过后：
   * <ul>
   *   <li>清空 verificationCode 字段（一次性使用）</li>
   *   <li>冻结 permissionSnapshot</li>
   *   <li>设置 effectiveIdentity</li>
   *   <li>状态流转到 AUTHORIZED</li>
   * </ul>
   * </p>
   *
   * @param rawCode 明文验证码
   * @param snapshot 权限快照（应用层预先解析）
   * @param identity 有效身份（应用层预先构造）
   * @param hasher 验证码哈希器
   * @param operator 操作人
   */
  public void authorize(
    String rawCode,
    PermissionSnapshot snapshot,
    EffectiveIdentity identity,
    VerificationCodeHasher hasher,
    UserNo operator
  ) {
    Objects.requireNonNull(rawCode, "rawCode");
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(hasher, "hasher");
    Objects.requireNonNull(operator, "operator");
    if (status != SecondaryAuthStatus.PENDING) {
      throw new DomainException(SecondaryAuthErrorCode.SESSION_NOT_PENDING);
    }
    if (verificationCode == null) {
      throw new DomainException(SecondaryAuthErrorCode.VERIFICATION_CODE_EXPIRED);
    }
    if (verificationCode.isExhausted()) {
      this.status = SecondaryAuthStatus.REJECTED;
      registerDomainEvent(SecondaryAuthRejected.of(
        id(), tellerAccountId, approverAccountId, operator));
      markUpdated(operator);
      throw new DomainException(SecondaryAuthErrorCode.VERIFICATION_CODE_EXHAUSTED);
    }
    if (!hasher.matches(rawCode, verificationCode.hashedCode())) {
      this.verificationCode = verificationCode.onAttemptFailed();
      markUpdated(operator);
      throw new DomainException(SecondaryAuthErrorCode.INVALID_VERIFICATION_CODE);
    }
    this.verificationCode = null;
    this.permissionSnapshot = snapshot;
    this.effectiveIdentity = identity;
    this.authorizedAt = LocalDateTime.now();
    this.status = SecondaryAuthStatus.AUTHORIZED;
    registerDomainEvent(SecondaryAuthCompleted.of(
      id(), tellerAccountId, approverAccountId, identity, snapshot, operator));
    markUpdated(operator);
  }

  /**
   * 记录一次校验失败（PENDING，剩余次数减 1，耗尽则自动 REJECTED）.
   *
   * <p>此方法用于应用层在 authorize 抛异常后显式记录失败（如需独立追踪）。
   * authorize 方法内部已自动调用 onAttemptFailed，因此通常无需手动调用此方法。</p>
   *
   * @param operator 操作人
   */
  public void recordFailedAttempt(UserNo operator) {
    Objects.requireNonNull(operator, "operator");
    if (status != SecondaryAuthStatus.PENDING) {
      throw new DomainException(SecondaryAuthErrorCode.SESSION_NOT_PENDING);
    }
    if (verificationCode == null) {
      throw new DomainException(SecondaryAuthErrorCode.VERIFICATION_CODE_EXPIRED);
    }
    this.verificationCode = verificationCode.onAttemptFailed();
    if (this.verificationCode.isExhausted()) {
      this.status = SecondaryAuthStatus.REJECTED;
      registerDomainEvent(SecondaryAuthRejected.of(
        id(), tellerAccountId, approverAccountId, operator));
    }
    markUpdated(operator);
  }

  /**
   * 重发验证码（PENDING，重置 verificationCode）.
   *
   * @param newCode 新的验证码值对象（应用层已哈希）
   * @param operator 操作人
   */
  public void resendVerificationCode(VerificationCode newCode, UserNo operator) {
    Objects.requireNonNull(newCode, "newCode");
    Objects.requireNonNull(operator, "operator");
    if (status != SecondaryAuthStatus.PENDING) {
      throw new DomainException(SecondaryAuthErrorCode.SESSION_NOT_PENDING);
    }
    this.verificationCode = newCode;
    // 重发视为新的发起事件
    registerDomainEvent(SecondaryAuthInitiated.of(
      id(), tellerAccountId, approverAccountId, operator));
    markUpdated(operator);
  }

  @Override
  protected void validateInvariants() {
    if (tellerAccountId == null) {
      throw new IllegalStateException("tellerAccountId cannot be null");
    }
    if (credentialOwner == null) {
      throw new IllegalStateException("credentialOwner cannot be null");
    }
    if (approverMobile == null) {
      throw new IllegalStateException("approverMobile cannot be null");
    }
    if (status == null) {
      throw new IllegalStateException("status cannot be null");
    }
    if (initiatedAt == null) {
      throw new IllegalStateException("initiatedAt cannot be null");
    }
    if (pendingExpiresAt == null) {
      throw new IllegalStateException("pendingExpiresAt cannot be null");
    }
    if (expiresAt == null) {
      throw new IllegalStateException("expiresAt cannot be null");
    }
    if (status == SecondaryAuthStatus.PENDING && verificationCode == null) {
      throw new IllegalStateException("verificationCode cannot be null when PENDING");
    }
    if (status == SecondaryAuthStatus.AUTHORIZED) {
      if (effectiveIdentity == null) {
        throw new IllegalStateException("effectiveIdentity cannot be null when AUTHORIZED");
      }
      if (permissionSnapshot == null) {
        throw new IllegalStateException("permissionSnapshot cannot be null when AUTHORIZED");
      }
      if (approverAccountId == null) {
        throw new IllegalStateException("approverAccountId cannot be null when AUTHORIZED");
      }
    }
  }

  // 查询方法

  public SecondaryAuthStatus status() { return status; }
  public UserNo tellerAccountId() { return tellerAccountId; }
  public UserNo approverAccountId() { return approverAccountId; }
  public CredentialOwner credentialOwner() { return credentialOwner; }
  public Mobile approverMobile() { return approverMobile; }
  public PlanNo planId() { return planId; }
  public VerificationCode verificationCode() { return verificationCode; }
  public EffectiveIdentity effectiveIdentity() { return effectiveIdentity; }
  public PermissionSnapshot permissionSnapshot() { return permissionSnapshot; }
  public LocalDateTime initiatedAt() { return initiatedAt; }
  public LocalDateTime pendingExpiresAt() { return pendingExpiresAt; }
  public LocalDateTime authorizedAt() { return authorizedAt; }
  public LocalDateTime expiresAt() { return expiresAt; }
  public String revokeReason() { return revokeReason; }

  public boolean isEffectiveAt(LocalDateTime now) {
    return status == SecondaryAuthStatus.AUTHORIZED
      && !expiresAt.isBefore(now)
      && (permissionSnapshot == null || !permissionSnapshot.isExpired(now));
  }
}
