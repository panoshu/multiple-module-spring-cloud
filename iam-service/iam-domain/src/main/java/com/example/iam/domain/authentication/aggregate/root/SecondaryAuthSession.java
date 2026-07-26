package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * 二次授权会话聚合根(网点渠道专属)。
 *
 * <p>用于网点柜员代理经办人办理业务的场景:柜员发起授权请求,经办人确认后,
 * 柜员在 {@link #expireAt} 之前可以借用经办人的权限操作业务。
 *
 * <p>授权完成时通过 {@link PermissionSnapshot} 冻结经办人当时的权限集合,
 * 即使后续经办人权限变更,本会话期间柜员使用的仍是冻结的权限。
 *
 * <p>状态机参照 {@link SecondaryAuthStatus}:
 * <ul>
 *   <li>{@code initiate} → PENDING</li>
 *   <li>PENDING → AUTHORIZED(经办人确认,冻结快照)</li>
 *   <li>PENDING → REJECTED(经办人拒绝)</li>
 *   <li>AUTHORIZED → EXPIRED(超时)</li>
 *   <li>AUTHORIZED → REVOKED(撤销)</li>
 *   <li>AUTHORIZED → CLOSED(柜员登出)</li>
 * </ul>
 *
 * <p>EXPIRED、REVOKED、CLOSED、REJECTED 均为终态。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public class SecondaryAuthSession extends AggregateRoot<SecondaryAuthSessionId> {

  private final Long tellerId;
  private final Long approverId;
  private final String customerNo;
  private final String planId;
  private Set<PermissionCode> permissionSnapshot;
  private SecondaryAuthStatus status;
  private final LocalDateTime initiatedAt;
  private LocalDateTime authorizedAt;
  private LocalDateTime expireAt;
  private String revokeReason;

  private SecondaryAuthSession(SecondaryAuthSessionId id, Long tellerId, Long approverId,
                                String customerNo, String planId,
                                Set<PermissionCode> permissionSnapshot,
                                SecondaryAuthStatus status,
                                LocalDateTime initiatedAt, LocalDateTime authorizedAt,
                                LocalDateTime expireAt, String revokeReason,
                                UserNo createdBy, UserNo updatedBy,
                                LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.tellerId = tellerId;
    this.approverId = approverId;
    this.customerNo = customerNo;
    this.planId = planId;
    this.permissionSnapshot = copySnapshot(permissionSnapshot);
    this.status = status;
    this.initiatedAt = initiatedAt;
    this.authorizedAt = authorizedAt;
    this.expireAt = expireAt;
    this.revokeReason = revokeReason;
    this.validateInvariants();
  }

  /**
   * 工厂方法:柜员发起二次授权请求(初始状态 PENDING)。
   *
   * @param id          会话 ID
   * @param tellerId    柜员用户 ID
   * @param approverId  经办人用户 ID
   * @param customerNo  客户编号(外部系统)
   * @param planId      计划编号(外部系统)
   * @param operator    操作人(发起柜员)
   * @return 新建的 PENDING 会话
   */
  public static SecondaryAuthSession initiate(SecondaryAuthSessionId id,
                                               Long tellerId, Long approverId,
                                               String customerNo, String planId,
                                               UserNo operator) {
    Objects.requireNonNull(tellerId, "tellerId cannot be null");
    Objects.requireNonNull(approverId, "approverId cannot be null");
    if (customerNo == null || customerNo.isBlank()) {
      throw new DomainException(IamAuthErrorCode.SECONDARY_AUTH_PLAN_MISMATCH)
          .withUserDetail("客户编号不能为空");
    }
    if (planId == null || planId.isBlank()) {
      throw new DomainException(IamAuthErrorCode.SECONDARY_AUTH_PLAN_MISMATCH)
          .withUserDetail("计划编号不能为空");
    }
    LocalDateTime now = LocalDateTime.now();
    return new SecondaryAuthSession(id, tellerId, approverId, customerNo, planId,
        null, SecondaryAuthStatus.PENDING,
        now, null, null, null,
        operator, operator, now, now, Version.initial());
  }

  /**
   * 工厂方法:从数据库重建聚合。
   */
  public static SecondaryAuthSession reconstitute(SecondaryAuthSessionId id,
                                                   Long tellerId, Long approverId,
                                                   String customerNo, String planId,
                                                   Set<PermissionCode> permissionSnapshot,
                                                   SecondaryAuthStatus status,
                                                   LocalDateTime initiatedAt, LocalDateTime authorizedAt,
                                                   LocalDateTime expireAt, String revokeReason,
                                                   UserNo createdBy, UserNo updatedBy,
                                                   LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    return new SecondaryAuthSession(id, tellerId, approverId, customerNo, planId,
        permissionSnapshot, status,
        initiatedAt, authorizedAt, expireAt, revokeReason,
        createdBy, updatedBy, createdAt, updatedAt, version);
  }

  /**
   * 经办人确认授权,冻结权限快照并转入 AUTHORIZED 状态。
   *
   * @param snapshot 经办人当前权限快照(由 PermissionResolver 计算)
   * @param expireAt 会话过期时间
   * @param approver 操作人(经办人)
   */
  public void authorize(PermissionSnapshot snapshot, LocalDateTime expireAt, UserNo approver) {
    Objects.requireNonNull(snapshot, "snapshot cannot be null");
    Objects.requireNonNull(expireAt, "expireAt cannot be null");
    if (!status.canAuthorize()) {
      throw new DomainException(IamAuthErrorCode.SECONDARY_AUTH_SESSION_COMPLETED)
          .withUserDetail("当前状态不允许授权: " + status)
          .withContext("sessionId", id().value())
          .withContext("currentStatus", status)
          .withContext("targetStatus", SecondaryAuthStatus.AUTHORIZED);
    }
    this.permissionSnapshot = copySnapshot(snapshot.permissions());
    this.authorizedAt = LocalDateTime.now();
    this.expireAt = expireAt;
    this.status = SecondaryAuthStatus.AUTHORIZED;
    markUpdated(approver);
  }

  /**
   * 经办人拒绝授权请求(PENDING → REJECTED)。
   *
   * @param approver 操作人(经办人)
   */
  public void reject(UserNo approver) {
    if (!status.canAuthorize()) {
      throw new DomainException(IamAuthErrorCode.SECONDARY_AUTH_SESSION_COMPLETED)
          .withUserDetail("当前状态不允许拒绝: " + status)
          .withContext("sessionId", id().value())
          .withContext("currentStatus", status)
          .withContext("targetStatus", SecondaryAuthStatus.REJECTED);
    }
    this.status = SecondaryAuthStatus.REJECTED;
    markUpdated(approver);
  }

  /**
   * 撤销授权(AUTHORIZED → REVOKED),需记录撤销原因。
   *
   * @param operator 操作人
   * @param reason   撤销原因(不能为空)
   */
  public void revoke(UserNo operator, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new DomainException(IamAuthErrorCode.SECONDARY_AUTH_SESSION_REVOKED)
          .withUserDetail("撤销原因不能为空");
    }
    if (status.isRevoked()) {
      throw new DomainException(IamAuthErrorCode.SECONDARY_AUTH_SESSION_REVOKED)
          .withUserDetail("二次授权会话已撤销,不可重复撤销")
          .withContext("sessionId", id().value())
          .withContext("status", status);
    }
    if (!status.canRevoke()) {
      throw new DomainException(IamAuthErrorCode.SECONDARY_AUTH_SESSION_REVOKED)
          .withUserDetail("当前状态不允许撤销: " + status)
          .withContext("sessionId", id().value())
          .withContext("currentStatus", status)
          .withContext("targetStatus", SecondaryAuthStatus.REVOKED);
    }
    this.status = SecondaryAuthStatus.REVOKED;
    this.revokeReason = reason;
    markUpdated(operator);
  }

  /**
   * 标记会话过期(AUTHORIZED → EXPIRED)。
   *
   * <p>由系统定时任务或登录前校验触发。
   */
  public void markExpired() {
    if (!status.canExpire()) {
      throw new DomainException(IamAuthErrorCode.SECONDARY_AUTH_SESSION_EXPIRED)
          .withUserDetail("当前状态不允许标记过期: " + status)
          .withContext("sessionId", id().value())
          .withContext("currentStatus", status)
          .withContext("targetStatus", SecondaryAuthStatus.EXPIRED);
    }
    this.status = SecondaryAuthStatus.EXPIRED;
  }

  /**
   * 关闭会话(AUTHORIZED → CLOSED),柜员登出时调用。
   *
   * @param operator 操作人(柜员)
   */
  public void close(UserNo operator) {
    if (!status.canClose()) {
      throw new DomainException(IamAuthErrorCode.SECONDARY_AUTH_SESSION_COMPLETED)
          .withUserDetail("当前状态不允许关闭: " + status)
          .withContext("sessionId", id().value())
          .withContext("currentStatus", status)
          .withContext("targetStatus", SecondaryAuthStatus.CLOSED);
    }
    this.status = SecondaryAuthStatus.CLOSED;
    markUpdated(operator);
  }

  /**
   * 判断会话在指定时刻是否生效。
   *
   * <p>生效条件:状态为 AUTHORIZED,且时刻在 [authorizedAt, expireAt] 区间内。
   *
   * @param moment 待判断的时刻
   * @return 生效返回 true
   */
  public boolean isEffectiveAt(LocalDateTime moment) {
    Objects.requireNonNull(moment, "moment cannot be null");
    if (!status.isAuthorized()) {
      return false;
    }
    if (authorizedAt == null || expireAt == null) {
      return false;
    }
    return !moment.isBefore(authorizedAt) && !moment.isAfter(expireAt);
  }

  /**
   * 判断本会话是否授权指定操作员(即操作员为发起柜员)。
   *
   * @param operatorId 待校验的操作员 ID
   * @return 会话生效且柜员 ID 匹配返回 true
   */
  public boolean authorizes(Long operatorId) {
    if (!status.isAuthorized()) {
      return false;
    }
    return tellerId.equals(operatorId);
  }

  public Long tellerId() { return tellerId; }
  public Long approverId() { return approverId; }
  public String customerNo() { return customerNo; }
  public String planId() { return planId; }
  public Set<PermissionCode> permissionSnapshot() {
    return permissionSnapshot == null ? null : Set.copyOf(permissionSnapshot);
  }
  public SecondaryAuthStatus status() { return status; }
  public LocalDateTime initiatedAt() { return initiatedAt; }
  public LocalDateTime authorizedAt() { return authorizedAt; }
  public LocalDateTime expireAt() { return expireAt; }
  public String revokeReason() { return revokeReason; }

  private static Set<PermissionCode> copySnapshot(Set<PermissionCode> source) {
    return source == null ? null : Set.copyOf(source);
  }

  @Override
  protected void validateInvariants() {
    if (tellerId == null) {
      throw new IllegalStateException("SecondaryAuthSession.tellerId cannot be null");
    }
    if (approverId == null) {
      throw new IllegalStateException("SecondaryAuthSession.approverId cannot be null");
    }
    if (customerNo == null || customerNo.isBlank()) {
      throw new IllegalStateException("SecondaryAuthSession.customerNo cannot be null or blank");
    }
    if (planId == null || planId.isBlank()) {
      throw new IllegalStateException("SecondaryAuthSession.planId cannot be null or blank");
    }
    if (status == null) {
      throw new IllegalStateException("SecondaryAuthSession.status cannot be null");
    }
    if (initiatedAt == null) {
      throw new IllegalStateException("SecondaryAuthSession.initiatedAt cannot be null");
    }
  }
}
