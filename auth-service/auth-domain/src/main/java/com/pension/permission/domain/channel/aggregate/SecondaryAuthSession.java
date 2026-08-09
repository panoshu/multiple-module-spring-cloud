package com.pension.permission.domain.channel.aggregate;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.errorcode.SecondaryAuthErrorCode;
import com.pension.permission.domain.channel.event.*;
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

  /**
   * 系统触发的操作（如超时过期）使用的操作人标识.
   */
  private static final UserNo SYSTEM_OPERATOR = UserNo.of("SYSTEM");

  private final UserNo tellerAccountId;
  private final CredentialOwner credentialOwner;
  private final Mobile approverMobile;
  private final PlanNo planId;
  private final LocalDateTime initiatedAt;
  private final LocalDateTime pendingExpiresAt;
  private final LocalDateTime expiresAt;
  private UserNo approverAccountId;
  private VerificationCode verificationCode;
  private EffectiveIdentity effectiveIdentity;
  private PermissionSnapshot permissionSnapshot;
  private SecondaryAuthStatus status;
  private LocalDateTime authorizedAt;
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
   * @param ctx 发起上下文（封装全部发起参数）
   * @return SecondaryAuthSession 实例
   */
  public static SecondaryAuthSession initiate(InitiateContext ctx) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(ctx.id(), "id");
    Objects.requireNonNull(ctx.tellerAccountId(), "tellerAccountId");
    Objects.requireNonNull(ctx.credentialOwner(), "credentialOwner");
    Objects.requireNonNull(ctx.approverAccountId(), "approverAccountId");
    Objects.requireNonNull(ctx.approverMobile(), "approverMobile");
    Objects.requireNonNull(ctx.verificationCode(), "verificationCode");
    Objects.requireNonNull(ctx.pendingTimeout(), "pendingTimeout");
    Objects.requireNonNull(ctx.sessionTimeout(), "sessionTimeout");
    Objects.requireNonNull(ctx.operator(), "operator");
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime pendingExpiresAt = now.plus(ctx.pendingTimeout());
    return new SecondaryAuthSession(
      ctx.id(), ctx.operator(),
      ctx.tellerAccountId(), ctx.approverAccountId(),
      ctx.credentialOwner(), ctx.approverMobile(),
      ctx.planId(), ctx.verificationCode(),
      now, pendingExpiresAt, now.plus(ctx.sessionTimeout()),
      SecondaryAuthStatus.PENDING);
  }

  /**
   * 从持久化数据重建聚合根.
   *
   * <p>不产生领域事件，仅恢复状态。</p>
   *
   * @param snapshot 持久化快照（封装全部重建字段）
   */
  public static SecondaryAuthSession reconstitute(ReconstituteSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    return new SecondaryAuthSession(
      snapshot.id(), snapshot.createdBy(), snapshot.updatedBy(),
      snapshot.createdAt(), snapshot.updatedAt(), snapshot.version(),
      snapshot.tellerAccountId(), snapshot.approverAccountId(),
      snapshot.credentialOwner(), snapshot.approverMobile(), snapshot.planId(),
      snapshot.verificationCode(), snapshot.effectiveIdentity(), snapshot.permissionSnapshot(),
      snapshot.status(), snapshot.initiatedAt(), snapshot.pendingExpiresAt(),
      snapshot.authorizedAt(), snapshot.expiresAt(), snapshot.revokeReason());
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
   * @param rawCode  明文验证码
   * @param snapshot 权限快照（应用层预先解析）
   * @param identity 有效身份（应用层预先构造）
   * @param hasher   验证码哈希器
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
   * 重发验证码（PENDING，重置 verificationCode）.
   *
   * @param newCode  新的验证码值对象（应用层已哈希）
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

  /**
   * 撤销授权（AUTHORIZED → REVOKED）.
   *
   * <p>经办人主动撤销或紧急收权时调用。</p>
   *
   * @param revoker 撤销人
   * @param reason  撤销原因
   */
  public void revoke(UserNo revoker, String reason) {
    Objects.requireNonNull(revoker, "revoker");
    Objects.requireNonNull(reason, "reason");
    if (status != SecondaryAuthStatus.AUTHORIZED) {
      throw new DomainException(SecondaryAuthErrorCode.SESSION_NOT_AUTHORIZED);
    }
    this.status = SecondaryAuthStatus.REVOKED;
    this.revokeReason = reason;
    registerDomainEvent(SecondaryAuthRevoked.of(
      id(), tellerAccountId, approverAccountId, reason, revoker));
    markUpdated(revoker);
  }

  /**
   * 柜员登出（AUTHORIZED → CLOSED）.
   *
   * @param operator 操作人
   */
  public void close(UserNo operator) {
    Objects.requireNonNull(operator, "operator");
    if (status != SecondaryAuthStatus.AUTHORIZED) {
      throw new DomainException(SecondaryAuthErrorCode.SESSION_NOT_AUTHORIZED);
    }
    this.status = SecondaryAuthStatus.CLOSED;
    registerDomainEvent(SecondaryAuthClosed.of(id(), tellerAccountId, operator));
    markUpdated(operator);
  }

  /**
   * 超时过期（PENDING → EXPIRED / AUTHORIZED → EXPIRED）.
   *
   * <p>仅活跃态（PENDING/AUTHORIZED）会检查超时，终态不做任何事。
   * PENDING 检查 pendingExpiresAt（5 分钟待授权窗口，不随验证码重发延期），
   * AUTHORIZED 检查 expiresAt 和 snapshot.expiresAt。</p>
   *
   * @param now 当前时间
   */
  public void expireIfTimeout(LocalDateTime now) {
    Objects.requireNonNull(now, "now");
    if (status.isTerminal()) {
      return;
    }
    boolean shouldExpire = false;
    if (status == SecondaryAuthStatus.PENDING) {
      if (pendingExpiresAt.isBefore(now)) {
        shouldExpire = true;
      }
    } else if (status == SecondaryAuthStatus.AUTHORIZED) {
      if (expiresAt.isBefore(now) || (permissionSnapshot != null && permissionSnapshot.isExpired(now))) {
        shouldExpire = true;
      }
    }
    if (!shouldExpire) {
      return;
    }
    this.status = SecondaryAuthStatus.EXPIRED;
    registerDomainEvent(SecondaryAuthExpired.of(id(), tellerAccountId, SYSTEM_OPERATOR));
    markUpdated(SYSTEM_OPERATOR);
  }

  @Override
  protected void validateInvariants() {
    if (tellerAccountId == null) {
      throw new DomainException(SecondaryAuthErrorCode.INVALID_DATA)
        .withLogDetail("tellerAccountId cannot be null");
    }
    if (credentialOwner == null) {
      throw new DomainException(SecondaryAuthErrorCode.INVALID_DATA)
        .withLogDetail("credentialOwner cannot be null");
    }
    if (approverMobile == null) {
      throw new DomainException(SecondaryAuthErrorCode.INVALID_DATA)
        .withLogDetail("approverMobile cannot be null");
    }
    if (status == null) {
      throw new DomainException(SecondaryAuthErrorCode.INVALID_DATA)
        .withLogDetail("status cannot be null");
    }
    if (initiatedAt == null) {
      throw new DomainException(SecondaryAuthErrorCode.INVALID_DATA)
        .withLogDetail("initiatedAt cannot be null");
    }
    if (pendingExpiresAt == null) {
      throw new DomainException(SecondaryAuthErrorCode.INVALID_DATA)
        .withLogDetail("pendingExpiresAt cannot be null");
    }
    if (expiresAt == null) {
      throw new DomainException(SecondaryAuthErrorCode.INVALID_DATA)
        .withLogDetail("expiresAt cannot be null");
    }
    if (status == SecondaryAuthStatus.PENDING && verificationCode == null) {
      throw new DomainException(SecondaryAuthErrorCode.SESSION_INVALID_STATE)
        .withLogDetail("verificationCode cannot be null when PENDING");
    }
    if (status == SecondaryAuthStatus.AUTHORIZED) {
      if (effectiveIdentity == null) {
        throw new DomainException(SecondaryAuthErrorCode.SESSION_INVALID_STATE)
          .withLogDetail("effectiveIdentity cannot be null when AUTHORIZED");
      }
      if (permissionSnapshot == null) {
        throw new DomainException(SecondaryAuthErrorCode.SESSION_INVALID_STATE)
          .withLogDetail("permissionSnapshot cannot be null when AUTHORIZED");
      }
      if (approverAccountId == null) {
        throw new DomainException(SecondaryAuthErrorCode.SESSION_INVALID_STATE)
          .withLogDetail("approverAccountId cannot be null when AUTHORIZED");
      }
    }
  }

  public SecondaryAuthStatus status() {
    return status;
  }

  public UserNo tellerAccountId() {
    return tellerAccountId;
  }

  // 查询方法

  public UserNo approverAccountId() {
    return approverAccountId;
  }

  public CredentialOwner credentialOwner() {
    return credentialOwner;
  }

  public Mobile approverMobile() {
    return approverMobile;
  }

  public PlanNo planId() {
    return planId;
  }

  public VerificationCode verificationCode() {
    return verificationCode;
  }

  public EffectiveIdentity effectiveIdentity() {
    return effectiveIdentity;
  }

  public PermissionSnapshot permissionSnapshot() {
    return permissionSnapshot;
  }

  public LocalDateTime initiatedAt() {
    return initiatedAt;
  }

  public LocalDateTime pendingExpiresAt() {
    return pendingExpiresAt;
  }

  public LocalDateTime authorizedAt() {
    return authorizedAt;
  }

  public LocalDateTime expiresAt() {
    return expiresAt;
  }

  public String revokeReason() {
    return revokeReason;
  }

  public boolean isEffectiveAt(LocalDateTime now) {
    return status == SecondaryAuthStatus.AUTHORIZED
      && !expiresAt.isBefore(now)
      && (permissionSnapshot == null || !permissionSnapshot.isExpired(now));
  }

  /**
   * 发起二次授权的参数对象.
   *
   * <p>封装 {@link #initiate(InitiateContext)} 所需的全部入参，避免方法参数超标
   * （规则 04 §10.1：单个方法参数不超过 5 个）。</p>
   */
  public record InitiateContext(
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
  }

  /**
   * 从持久化数据重建聚合根的快照参数对象.
   *
   * <p>封装 {@link #reconstitute(ReconstituteSnapshot)} 所需的全部持久化字段，
   * 避免 20 个参数散传导致顺序错配。</p>
   */
  public record ReconstituteSnapshot(
    SecondaryAuthSessionId id,
    UserNo createdBy, UserNo updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt,
    Version version,
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
  }
}
