package com.pension.permission.domain.authorization.aggregate;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.valueobject.ValidityPeriod;
import com.pension.permission.domain.authorization.enumeration.Effect;
import com.pension.permission.domain.authorization.enumeration.GrantOrigin;
import com.pension.permission.domain.authorization.enumeration.GrantStatus;
import com.pension.permission.domain.authorization.enumeration.GrantType;
import com.pension.permission.domain.authorization.event.GrantApproved;
import com.pension.permission.domain.authorization.event.GrantCreated;
import com.pension.permission.domain.authorization.event.GrantRejected;
import com.pension.permission.domain.authorization.event.GrantRevoked;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.authorization.valueobject.ScopeRule;
import com.pension.permission.domain.authorization.valueobject.subject.GrantSubject;
import com.pension.permission.types.GrantId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 授权策略主记录——权限与授权域的核心聚合根。能力层配置、经办的主体授权、
 * 计划间代办、跨企业委托，全部都是一条Grant，只是subject/scope/effect/origin的取值不同。
 * 注意：角色模板派生的权限不走这个聚合根持久化，而是在判定时实时解析
 * (见 assignment.EffectivePermissionService)——所以这里不再有"derivedFrom"这种
 * 记录来源身份分配的字段。Grant只承载"需要显式配置/审批的授权"，不承载
 * "从其他数据实时推导出来、本身没有独立生命周期"的权限。
 */
public class Grant extends AggregateRoot<GrantId> {

  private final GrantSubject subject;
  private final List<ScopeRule> scopeRules;
  private final Set<Permission> permissions;
  private final GrantType grantType;
  private final GrantOrigin origin;
  private final Effect effect;
  /**
   * 代办场景下：授权方计划 / 接受方(目标)计划；非代办场景可为null
   */
  private final PlanNo sourcePlanNo;
  private final PlanNo targetPlanNo;
  private GrantStatus status;
  private ValidityPeriod validityPeriod;

  // ==========================================
  // 1. 构造方法 (私有化，强制通过静态工厂方法实例化)
  // ==========================================

  /**
   * 场景1: 业务创建 (New)
   */
  private Grant(
    GrantId id,
    UserNo creator,
    GrantSubject subject,
    List<ScopeRule> scopeRules,
    Set<Permission> permissions,
    GrantType grantType,
    GrantOrigin origin,
    Effect effect,
    GrantStatus initialStatus,
    ValidityPeriod validityPeriod,
    PlanNo sourcePlanNo,
    PlanNo targetPlanNo
  ) {
    // 调用基类的业务创建构造方法
    super(id, creator);

    this.subject = subject;
    // 【关键】防御性拷贝：防止外部修改集合影响聚合根内部状态
    this.scopeRules = scopeRules != null ? new ArrayList<>(scopeRules) : new ArrayList<>();
    this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
    this.grantType = grantType;
    this.origin = origin;
    this.effect = effect;
    this.status = initialStatus;
    // 默认兜底策略：如果未传入时间期限，则视为长期有效
    this.validityPeriod = validityPeriod != null ? validityPeriod : ValidityPeriod.infinite();
    this.sourcePlanNo = sourcePlanNo;
    this.targetPlanNo = targetPlanNo;

    // 【关键】子类必须在自己的构造函数末尾显式调用 validateInvariants()
    this.validateInvariants();

    this.registerDomainEvent(GrantCreated.of(this.id(), creator));
  }

  /**
   * 场景2: 从数据库重建 (Reconstitute)
   */
  private Grant(
    GrantId id,
    UserNo createdBy,
    UserNo updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Version version,
    GrantSubject subject,
    List<ScopeRule> scopeRules,
    Set<Permission> permissions,
    GrantType grantType,
    GrantOrigin origin,
    Effect effect,
    GrantStatus status,
    ValidityPeriod validityPeriod,
    PlanNo sourcePlanNo,
    PlanNo targetPlanNo
  ) {
    // 调用基类的重建构造方法
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);

    this.subject = subject;
    // 【关键】防御性拷贝
    this.scopeRules = scopeRules != null ? new ArrayList<>(scopeRules) : new ArrayList<>();
    this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
    this.grantType = grantType;
    this.origin = origin;
    this.effect = effect;
    this.status = status;
    this.validityPeriod = validityPeriod != null ? validityPeriod : ValidityPeriod.infinite();
    this.sourcePlanNo = sourcePlanNo;
    this.targetPlanNo = targetPlanNo;

    // 【关键】子类必须在自己的构造函数末尾显式调用 validateInvariants()
    this.validateInvariants();
  }

  // ==========================================
  // 2. 静态工厂方法 (DDD 推荐实践，提供清晰的语义)
  // ==========================================

  public static Grant create(
    GrantId id, UserNo creator, GrantSubject subject,
    List<ScopeRule> scopeRules, Set<Permission> permissions,
    GrantType grantType, GrantOrigin origin, Effect effect,
    GrantStatus initialStatus, ValidityPeriod validityPeriod,
    PlanNo sourcePlanNo, PlanNo targetPlanNo
  ) {
    return new Grant(id, creator, subject, scopeRules, permissions, grantType, origin, effect,
      initialStatus, validityPeriod, sourcePlanNo, targetPlanNo);
  }

  public static Grant reconstitute(
    GrantId id, UserNo createdBy, UserNo updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
    GrantSubject subject, List<ScopeRule> scopeRules, Set<Permission> permissions,
    GrantType grantType, GrantOrigin origin, Effect effect, GrantStatus status,
    ValidityPeriod validityPeriod, PlanNo sourcePlanNo, PlanNo targetPlanNo
  ) {
    return new Grant(id, createdBy, updatedBy, createdAt, updatedAt, version,
      subject, scopeRules, permissions, grantType, origin, effect, status,
      validityPeriod, sourcePlanNo, targetPlanNo);
  }


  // ---- 状态流转行为，均登记对应的领域事件 ----

  public Effect effect() {
    return effect;
  }

  public List<ScopeRule> scopeRules() {
    return List.copyOf(scopeRules);
  }

  public GrantSubject subject() {
    return subject;
  }

  public Set<Permission> permissions() {
    return Set.copyOf(permissions);
  }

  public void approve(UserNo approver) {
    requireStatus(GrantStatus.PENDING_APPROVAL);
    this.status = GrantStatus.EFFECTIVE;
    registerDomainEvent(GrantApproved.of(this.id(), approver));
  }

  public void reject(UserNo rejecter) {
    requireStatus(GrantStatus.PENDING_APPROVAL);
    this.status = GrantStatus.REJECTED;
    registerDomainEvent(GrantRejected.of(this.id(), rejecter));
  }

  /**
   * 撤销：紧急场景(账号冻结/代办关系撤销)应立即调用，不等生效窗口自然结束
   */
  public void revoke(UserNo revoker) {
    requireStatus(GrantStatus.EFFECTIVE);
    this.status = GrantStatus.REVOKED;
    registerDomainEvent(GrantRevoked.of(this.id(), revoker));
  }

  private void requireStatus(GrantStatus expected) {
    if (this.status != expected) {
      throw new IllegalStateException("状态不满足操作要求，当前=" + status + "，期望=" + expected);
    }
  }

  // ---- 查询/判定用的辅助方法 ----

  /**
   * 判断在指定时间点，该授权是否处于“激活且生效”的状态。
   * <p>
   * 激活生效需要同时满足三个维度的条件：
   * 1. 状态维度 (Status)：必须是已激活/已授予状态。
   * 2. 效果维度 (Effect)：必须是允许授权 (ALLOW/PERMIT)，如果是 DENY 则不视为有效授权。
   * 3. 时间维度 (Time)：当前时间必须在有效期 (validityPeriod) 内。
   *
   * @param now 指定的时间点
   * @return 是否激活生效
   */
  public boolean isActiveAt(LocalDateTime now) {
    if (now == null) {
      throw new IllegalArgumentException("Time 'now' cannot be null.");
    }

    // 1. 状态校验：必须是已激活/生效状态
    // (请根据您实际的 GrantStatus 枚举值调整，例如 ACTIVE, GRANTED, APPROVED)
    if (status != GrantStatus.EFFECTIVE) {
      return false;
    }

    // 2. 效果校验：必须是允许授权
    // (请根据您实际的 Effect 枚举值调整，例如 ALLOW, DENY)
    boolean isEffectAllowed = (this.effect == Effect.ALLOW || this.effect == Effect.DENY);
    if (!isEffectAllowed) {
      return false;
    }

    // 3. 时间校验：委托给值对象进行判断，完美复用 ValidityPeriod 的能力
    return this.validityPeriod.isEffective(now);
  }

  /**
   * 检查当前授权是否已过期 (快捷方法)
   */
  public boolean isExpired() {
    return this.validityPeriod.expired(LocalDateTime.now());
  }

  /**
   * 能力层视角：这条Grant是否覆盖某个业务(不区分具体操作)
   */
  public boolean coversBusiness(BusinessCode business) {
    return permissions.stream().anyMatch(p -> p.businessCode().equals(business));
  }

  /**
   * 主体层视角：这条Grant是否覆盖某个具体的(业务,操作)
   */
  public boolean grants(Permission permission) {
    return permissions.stream()
      .anyMatch(p -> p.covers(permission.businessCode(), permission.actionCode()));
  }

  /**
   * 授权延期操作 (示例)
   */
  public void extendValidity(java.time.Duration duration, UserNo operator) {
    // 直接调用值对象的行为，返回新实例替换老实例 (保持不可变性)
    this.validityPeriod = this.validityPeriod.extend(duration);
    this.markUpdated(operator);
    // this.registerDomainEvent(new GrantExtendedEvent(this.id(), duration));
  }

  @Override
  protected void validateInvariants() {
    if (this.subject == null) {
      throw new IllegalArgumentException("Grant subject cannot be null.");
    }
    if (this.grantType == null) {
      throw new IllegalArgumentException("Grant type cannot be null.");
    }
    if (this.origin == null) {
      throw new IllegalArgumentException("Grant origin cannot be null.");
    }
    if (this.effect == null) {
      throw new IllegalArgumentException("Grant effect cannot be null.");
    }
    if (this.status == null) {
      throw new IllegalArgumentException("Grant status cannot be null.");
    }
    if (this.validityPeriod == null) {
      throw new IllegalArgumentException("Validity period cannot be null.");
    }
  }
}
