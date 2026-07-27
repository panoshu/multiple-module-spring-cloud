package com.example.iam.domain.authorization.aggregate.root;

import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationPermission;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationType;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.event.PlanDelegationActivatedEvent;
import com.example.iam.domain.authorization.event.PlanDelegationCreatedEvent;
import com.example.iam.domain.authorization.event.PlanDelegationRevokedEvent;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.iam.types.PlanDelegationId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 计划代办关系聚合根 - 声明"计划 A 授权计划 B 代办某些业务"的关系。
 *
 * <p>设计文档 3.4.5 节字段:
 * <ul>
 *   <li>{@code delegationCode} - 代办编码(全局唯一)</li>
 *   <li>{@code delegatorPlanNo} - 授权方计划编号(出借权限的计划)</li>
 *   <li>{@code delegateePlanNo} - 被授权方计划编号(获得权限的计划)</li>
 *   <li>{@code delegationType} - 代办类型(ALL_OPERATORS/SPECIFIC_OPERATORS)</li>
 *   <li>{@code designatedOperators} - 指定操作员集合(仅 SPECIFIC_OPERATORS 类型时使用)</li>
 *   <li>{@code delegatedPermissions} - 授权权限集合(声明被授权方可执行的业务+动作)</li>
 *   <li>{@code status} - 状态(ACTIVE/REVOKED/EXPIRED)</li>
 *   <li>{@code effectiveAt} - 生效时间</li>
 *   <li>{@code expireAt} - 失效时间</li>
 * </ul>
 *
 * <p>两种代办类型:
 * <ul>
 *   <li>{@code ALL_OPERATORS}:授权方计划下所有经办都拥有代办授权</li>
 *   <li>{@code SPECIFIC_OPERATORS}:仅授权方指定的经办获得代办授权</li>
 * </ul>
 *
 * <p>状态机参照 {@link DelegationStatus}:
 * <ul>
 *   <li>{@code create} → ACTIVE</li>
 *   <li>ACTIVE → REVOKED(主动撤销,终态)</li>
 *   <li>ACTIVE → EXPIRED(到达失效时间,终态)</li>
 * </ul>
 *
 * <p>核心查询:
 * <ul>
 *   <li>{@link #authorizes(Long)} - 判断是否授权指定操作员</li>
 *   <li>{@link #permissionCodesFor(Long, BusinessCode)} - 获取代办授权的权限码集合</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public class PlanDelegation extends AggregateRoot<PlanDelegationId> {

  private final String delegationCode;
  private final String delegatorPlanNo;
  private final String delegateePlanNo;
  private final DelegationType delegationType;
  private Set<Long> designatedOperators;
  private final Set<DelegationPermission> delegatedPermissions;
  private DelegationStatus status;
  private final LocalDateTime effectiveAt;
  private final LocalDateTime expireAt;

  private PlanDelegation(PlanDelegationId id, String delegationCode,
                        String delegatorPlanNo, String delegateePlanNo,
                        DelegationType delegationType,
                        Set<Long> designatedOperators,
                        Set<DelegationPermission> delegatedPermissions,
                        DelegationStatus status,
                        LocalDateTime effectiveAt, LocalDateTime expireAt,
                        UserNo createdBy, UserNo updatedBy,
                        LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.delegationCode = delegationCode;
    this.delegatorPlanNo = delegatorPlanNo;
    this.delegateePlanNo = delegateePlanNo;
    this.delegationType = delegationType;
    this.designatedOperators = copyOperators(designatedOperators);
    this.delegatedPermissions = copyPermissions(delegatedPermissions);
    this.status = status;
    this.effectiveAt = effectiveAt;
    this.expireAt = expireAt;
    this.validateInvariants();
  }

  /**
   * 工厂方法:创建代办关系(初始状态 ACTIVE)。
   *
   * @param id                   代办关系 ID
   * @param delegationCode       代办编码(全局唯一)
   * @param delegatorPlanNo      授权方计划编号
   * @param delegateePlanNo      被授权方计划编号
   * @param delegationType       代办类型
   * @param designatedOperators  指定操作员(SPECIFIC_OPERATORS 时非空,ALL_OPERATORS 时忽略)
   * @param delegatedPermissions 授权权限集合(非空)
   * @param effectiveAt          生效时间(可空,空表示立即生效)
   * @param expireAt             失效时间(可空,空表示永久)
   * @param createdBy            创建人
   * @return 新建的代办关系聚合根
   */
  public static PlanDelegation create(PlanDelegationId id, String delegationCode,
                                      String delegatorPlanNo, String delegateePlanNo,
                                      DelegationType delegationType,
                                      Set<Long> designatedOperators,
                                      Set<DelegationPermission> delegatedPermissions,
                                      LocalDateTime effectiveAt, LocalDateTime expireAt,
                                      UserNo createdBy) {
    validateCommon(delegationCode, delegatorPlanNo, delegateePlanNo,
        delegationType, designatedOperators, delegatedPermissions, effectiveAt, expireAt);
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime effective = effectiveAt != null ? effectiveAt : now;
    Set<Long> operators = delegationType == DelegationType.ALL_OPERATORS
        ? Set.of()
        : copyOperators(designatedOperators);
    PlanDelegation delegation = new PlanDelegation(id, delegationCode, delegatorPlanNo, delegateePlanNo,
        delegationType, operators, delegatedPermissions,
        DelegationStatus.ACTIVE, effective, expireAt,
        createdBy, createdBy, now, now, Version.initial());
    delegation.registerDomainEvent(PlanDelegationCreatedEvent.of(
        id, delegationCode, delegatorPlanNo, delegateePlanNo, delegationType, createdBy));
    // create 直接进入 ACTIVE 状态,同步触发 Activated 事件
    delegation.registerDomainEvent(PlanDelegationActivatedEvent.of(
        id, delegationCode, delegateePlanNo, createdBy));
    return delegation;
  }

  /**
   * 工厂方法:从数据库重建聚合。
   */
  public static PlanDelegation reconstitute(PlanDelegationId id, String delegationCode,
                                            String delegatorPlanNo, String delegateePlanNo,
                                            DelegationType delegationType,
                                            Set<Long> designatedOperators,
                                            Set<DelegationPermission> delegatedPermissions,
                                            DelegationStatus status,
                                            LocalDateTime effectiveAt, LocalDateTime expireAt,
                                            UserNo createdBy, UserNo updatedBy,
                                            LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    return new PlanDelegation(id, delegationCode, delegatorPlanNo, delegateePlanNo,
        delegationType, designatedOperators, delegatedPermissions,
        status, effectiveAt, expireAt,
        createdBy, updatedBy, createdAt, updatedAt, version);
  }

  /**
   * 撤销代办关系(终态)。
   *
   * <p>仅 ACTIVE 状态可撤销,撤销后不可恢复。
   *
   * @param operator 操作人
   * @param reason   撤销原因(可空)
   */
  public void revoke(UserNo operator, String reason) {
    if (!status.canRevoke()) {
      throw new DomainException(IamAuthzErrorCode.PLAN_DELEGATION_STATUS_INVALID)
          .withUserDetail("当前状态不允许撤销: " + status)
          .withContext("currentStatus", status.name())
          .withContext("targetStatus", DelegationStatus.REVOKED.name());
    }
    this.status = DelegationStatus.REVOKED;
    markUpdated(operator);
    registerDomainEvent(PlanDelegationRevokedEvent.of(
        id(), delegationCode, delegateePlanNo, reason, operator));
  }

  /**
   * 标记代办关系过期(由定时任务触发)。
   *
   * <p>仅 ACTIVE 状态可标记过期。
   *
   * @param operator 操作人(系统账号)
   */
  public void markExpired(UserNo operator) {
    if (!status.canExpire()) {
      throw new DomainException(IamAuthzErrorCode.PLAN_DELEGATION_STATUS_INVALID)
          .withUserDetail("当前状态不允许标记过期: " + status)
          .withContext("currentStatus", status.name())
          .withContext("targetStatus", DelegationStatus.EXPIRED.name());
    }
    this.status = DelegationStatus.EXPIRED;
    markUpdated(operator);
  }

  /**
   * 判断代办关系是否在指定时刻生效。
   *
   * @param moment 判断时刻(可空,空表示当前)
   * @return 生效返回 true
   */
  public boolean isEffectiveAt(LocalDateTime moment) {
    if (!status.isActive()) {
      return false;
    }
    LocalDateTime now = moment != null ? moment : LocalDateTime.now();
    if (now.isBefore(effectiveAt)) {
      return false;
    }
    return expireAt == null || now.isBefore(expireAt);
  }

  /**
   * 判断是否授权指定操作员。
   *
   * <p>授权逻辑:
   * <ul>
   *   <li>ALL_OPERATORS:所有操作员均被授权(返回 true)</li>
   *   <li>SPECIFIC_OPERATORS:仅 designatedOperators 中的操作员被授权</li>
   * </ul>
   *
   * @param operatorId 操作员 ID
   * @return 授权返回 true
   */
  public boolean authorizes(Long operatorId) {
    Objects.requireNonNull(operatorId, "operatorId cannot be null");
    if (delegationType == DelegationType.ALL_OPERATORS) {
      return true;
    }
    return designatedOperators.contains(operatorId);
  }

  /**
   * 获取代办关系授权的权限码集合(用于 PermissionResolver 合并代办授权)。
   *
   * <p>当 operatorId 被授权时,返回所有 delegatedPermissions 对应的权限码;
   * 否则返回空集合。
   *
   * @param operatorId 操作员 ID
   * @return 权限码集合(不可变)
   */
  public Set<PermissionCode> permissionCodesFor(Long operatorId) {
    Objects.requireNonNull(operatorId, "operatorId cannot be null");
    if (!authorizes(operatorId)) {
      return Set.of();
    }
    return delegatedPermissions.stream()
        .map(DelegationPermission::toPermissionCode)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * 获取指定操作员和业务下的代办授权权限码集合。
   *
   * <p>用于精确查询"某操作员对某业务获得的代办授权"。
   *
   * @param operatorId   操作员 ID
   * @param businessCode 业务编码
   * @return 权限码集合(可能为空)
   */
  public Set<PermissionCode> permissionCodesFor(Long operatorId, BusinessCode businessCode) {
    Objects.requireNonNull(operatorId, "operatorId cannot be null");
    Objects.requireNonNull(businessCode, "businessCode cannot be null");
    if (!authorizes(operatorId)) {
      return Set.of();
    }
    return delegatedPermissions.stream()
        .filter(p -> p.businessCode().equals(businessCode))
        .map(DelegationPermission::toPermissionCode)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * 判断本代办关系是否授权了指定业务编码和动作。
   *
   * @param businessCode 业务编码
   * @param action       业务动作
   * @return 包含返回 true
   */
  public boolean containsPermission(BusinessCode businessCode, Action action) {
    Objects.requireNonNull(businessCode, "businessCode cannot be null");
    Objects.requireNonNull(action, "action cannot be null");
    return delegatedPermissions.stream().anyMatch(p -> p.matches(businessCode, action));
  }

  public String delegationCode() { return delegationCode; }
  public String delegatorPlanNo() { return delegatorPlanNo; }
  public String delegateePlanNo() { return delegateePlanNo; }
  public DelegationType delegationType() { return delegationType; }
  public Set<Long> designatedOperators() { return Set.copyOf(designatedOperators); }
  public Set<DelegationPermission> delegatedPermissions() { return Set.copyOf(delegatedPermissions); }
  public DelegationStatus status() { return status; }
  public LocalDateTime effectiveAt() { return effectiveAt; }
  public LocalDateTime expireAt() { return expireAt; }

  @Override
  protected void validateInvariants() {
    if (delegationCode == null || delegationCode.isBlank()) {
      throw new IllegalStateException("PlanDelegation.delegationCode cannot be null or blank");
    }
    if (delegatorPlanNo == null || delegatorPlanNo.isBlank()) {
      throw new IllegalStateException("PlanDelegation.delegatorPlanNo cannot be null or blank");
    }
    if (delegateePlanNo == null || delegateePlanNo.isBlank()) {
      throw new IllegalStateException("PlanDelegation.delegateePlanNo cannot be null or blank");
    }
    if (delegationType == null) {
      throw new IllegalStateException("PlanDelegation.delegationType cannot be null");
    }
    if (delegatedPermissions == null || delegatedPermissions.isEmpty()) {
      throw new IllegalStateException("PlanDelegation.delegatedPermissions cannot be null or empty");
    }
    if (status == null) {
      throw new IllegalStateException("PlanDelegation.status cannot be null");
    }
    if (effectiveAt == null) {
      throw new IllegalStateException("PlanDelegation.effectiveAt cannot be null");
    }
    if (expireAt != null && !expireAt.isAfter(effectiveAt)) {
      throw new IllegalStateException("PlanDelegation.expireAt must be after effectiveAt");
    }
    if (delegationType == DelegationType.SPECIFIC_OPERATORS
        && (designatedOperators == null || designatedOperators.isEmpty())) {
      throw new IllegalStateException(
          "PlanDelegation.designatedOperators cannot be empty for SPECIFIC_OPERATORS type");
    }
    if (delegatorPlanNo.equals(delegateePlanNo)) {
      throw new IllegalStateException(
          "PlanDelegation.delegatorPlanNo cannot equal delegateePlanNo (self-delegation)");
    }
  }

  private static void validateCommon(String delegationCode,
                                     String delegatorPlanNo, String delegateePlanNo,
                                     DelegationType delegationType,
                                     Set<Long> designatedOperators,
                                     Set<DelegationPermission> delegatedPermissions,
                                     LocalDateTime effectiveAt, LocalDateTime expireAt) {
    if (delegationCode == null || delegationCode.isBlank()) {
      throw new DomainException(IamAuthzErrorCode.PLAN_DELEGATION_CODE_DUPLICATE)
          .withUserDetail("代办编码不能为空");
    }
    if (delegatorPlanNo == null || delegatorPlanNo.isBlank()) {
      throw new DomainException(IamAuthzErrorCode.PLAN_DELEGATION_NOT_FOUND)
          .withUserDetail("授权方计划编号不能为空");
    }
    if (delegateePlanNo == null || delegateePlanNo.isBlank()) {
      throw new DomainException(IamAuthzErrorCode.PLAN_DELEGATION_NOT_FOUND)
          .withUserDetail("被授权方计划编号不能为空");
    }
    if (delegatorPlanNo.equals(delegateePlanNo)) {
      throw new DomainException(IamAuthzErrorCode.PLAN_DELEGATION_SELF_DELEGATION)
          .withUserDetail("授权方和被授权方不能相同");
    }
    Objects.requireNonNull(delegationType, "delegationType cannot be null");
    if (delegationType == DelegationType.SPECIFIC_OPERATORS
        && (designatedOperators == null || designatedOperators.isEmpty())) {
      throw new DomainException(IamAuthzErrorCode.DELEGATION_OPERATOR_NOT_SPECIFIED)
          .withUserDetail("指定经办类型必须提供操作员列表");
    }
    if (delegatedPermissions == null || delegatedPermissions.isEmpty()) {
      throw new DomainException(IamAuthzErrorCode.DELEGATION_PERMISSION_EMPTY)
          .withUserDetail("代办权限不能为空");
    }
    if (expireAt != null && effectiveAt != null && !expireAt.isAfter(effectiveAt)) {
      throw new DomainException(IamAuthzErrorCode.PLAN_DELEGATION_STATUS_INVALID)
          .withUserDetail("失效时间必须晚于生效时间");
    }
  }

  private static Set<Long> copyOperators(Set<Long> source) {
    return source == null ? new HashSet<>() : new HashSet<>(source);
  }

  private static Set<DelegationPermission> copyPermissions(Set<DelegationPermission> source) {
    return source == null ? new HashSet<>() : new HashSet<>(source);
  }
}
