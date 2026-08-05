package com.pension.permission.domain.channel.aggregate;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.event.SecondaryAuthInitiated;
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
  private LocalDateTime authorizedAt;
  private final LocalDateTime expiresAt;
  private String revokeReason;

  private SecondaryAuthSession(
    SecondaryAuthSessionId id, UserNo creator,
    UserNo tellerAccountId, UserNo approverAccountId,
    CredentialOwner credentialOwner, Mobile approverMobile,
    PlanNo planId,
    VerificationCode verificationCode,
    LocalDateTime initiatedAt, LocalDateTime expiresAt,
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
    LocalDateTime initiatedAt, LocalDateTime authorizedAt,
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
    return new SecondaryAuthSession(
      id, operator,
      tellerAccountId, approverAccountId,
      credentialOwner, approverMobile,
      planId, verificationCode,
      now, now.plus(sessionTimeout),
      SecondaryAuthStatus.PENDING);
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
  public LocalDateTime authorizedAt() { return authorizedAt; }
  public LocalDateTime expiresAt() { return expiresAt; }
  public String revokeReason() { return revokeReason; }

  public boolean isEffectiveAt(LocalDateTime now) {
    return status == SecondaryAuthStatus.AUTHORIZED
      && !expiresAt.isBefore(now)
      && (permissionSnapshot == null || !permissionSnapshot.isExpired(now));
  }
}
