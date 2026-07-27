package com.example.iam.domain.authorization.aggregate.root;

import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.OverrideMode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionMatchContext;
import com.example.iam.domain.authorization.aggregate.valueobject.RuleStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.domain.authorization.event.PermissionRuleCreatedEvent;
import com.example.iam.domain.authorization.event.PermissionRuleDisabledEvent;
import com.example.iam.domain.authorization.event.PermissionRuleEnabledEvent;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.iam.types.PermissionRuleId;
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
 * 权限规则聚合根 - 授权域的核心配置单元。
 *
 * <p>每条规则声明"在某主体维度下,对某业务的某些动作是否允许"。
 * 通过 {@link SubjectType} 标识主体维度(决定优先级),通过 {@link OverrideMode} 声明对低层级规则的作用。
 *
 * <p>设计文档 3.4.4 节字段:
 * <ul>
 *   <li>{@code ruleCode} - 规则编码(全局唯一)</li>
 *   <li>{@code ruleName} - 规则名称(展示用)</li>
 *   <li>{@code subjectType} - 主体维度(CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER)</li>
 *   <li>{@code subjectId} - 主体标识(对应维度的具体值,如客户编号/计划编号等)</li>
 *   <li>{@code businessCode} - 业务编码(关联 BusinessDefinition)</li>
 *   <li>{@code allowedActions} - 授权动作集合(HANDLE/QUERY/AUDIT 等)</li>
 *   <li>{@code inheritToChildren} - 是否继承给下属企业(仅 CUSTOMER 级有意义)</li>
 *   <li>{@code overrideMode} - 覆盖模式(ADD 扩展/REMOVE 收紧)</li>
 *   <li>{@code priority} - 优先级(可空,空则使用 SubjectType.priority)</li>
 *   <li>{@code status} - 规则状态(ACTIVE/DISABLED)</li>
 *   <li>{@code effectiveAt} - 生效时间</li>
 *   <li>{@code expireAt} - 失效时间(可空,表示永久)</li>
 * </ul>
 *
 * <p>状态机参照 {@link RuleStatus}:
 * <ul>
 *   <li>{@code create} → ACTIVE</li>
 *   <li>ACTIVE → DISABLED(管理员禁用)</li>
 *   <li>DISABLED → ACTIVE(管理员启用)</li>
 * </ul>
 *
 * <p>匹配逻辑见 {@link #matches(PermissionMatchContext)}:根据 subjectType 取对应维度,
 * 与 subjectId 比较,相等即匹配;且规则需处于生效状态({@link #isEffectiveAt(LocalDateTime)})。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public class PermissionRule extends AggregateRoot<PermissionRuleId> {

  private final String ruleCode;
  private final String ruleName;
  private final SubjectType subjectType;
  private final String subjectId;
  private final BusinessCode businessCode;
  private final Set<Action> allowedActions;
  private final boolean inheritToChildren;
  private final OverrideMode overrideMode;
  private final Integer priority;
  private RuleStatus status;
  private final LocalDateTime effectiveAt;
  private final LocalDateTime expireAt;

  private PermissionRule(PermissionRuleId id, String ruleCode, String ruleName,
                         SubjectType subjectType, String subjectId,
                         BusinessCode businessCode, Set<Action> allowedActions,
                         boolean inheritToChildren, OverrideMode overrideMode, Integer priority,
                         RuleStatus status, LocalDateTime effectiveAt, LocalDateTime expireAt,
                         UserNo createdBy, UserNo updatedBy,
                         LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.ruleCode = ruleCode;
    this.ruleName = ruleName;
    this.subjectType = subjectType;
    this.subjectId = subjectId;
    this.businessCode = businessCode;
    this.allowedActions = copyActions(allowedActions);
    this.inheritToChildren = inheritToChildren;
    this.overrideMode = overrideMode;
    this.priority = priority;
    this.status = status;
    this.effectiveAt = effectiveAt;
    this.expireAt = expireAt;
    this.validateInvariants();
  }

  /**
   * 工厂方法:创建新权限规则(初始状态 ACTIVE)。
   *
   * @param id               规则 ID
   * @param ruleCode         规则编码(全局唯一)
   * @param ruleName         规则名称
   * @param subjectType      主体维度
   * @param subjectId        主体标识
   * @param businessCode     业务编码
   * @param allowedActions   授权动作集合(非空)
   * @param inheritToChildren 是否继承给下属企业
   * @param overrideMode     覆盖模式
   * @param priority         优先级(可空)
   * @param effectiveAt      生效时间(可空,空表示立即生效)
   * @param expireAt         失效时间(可空,空表示永久)
   * @param createdBy        创建人
   * @return 新建的权限规则聚合根
   */
  public static PermissionRule create(PermissionRuleId id, String ruleCode, String ruleName,
                                      SubjectType subjectType, String subjectId,
                                      BusinessCode businessCode, Set<Action> allowedActions,
                                      boolean inheritToChildren, OverrideMode overrideMode,
                                      Integer priority,
                                      LocalDateTime effectiveAt, LocalDateTime expireAt,
                                      UserNo createdBy) {
    validateCommon(ruleCode, ruleName, subjectType, subjectId, businessCode,
        allowedActions, overrideMode, effectiveAt, expireAt);
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime effective = effectiveAt != null ? effectiveAt : now;
    PermissionRule rule = new PermissionRule(id, ruleCode, ruleName, subjectType, subjectId,
        businessCode, allowedActions, inheritToChildren, overrideMode, priority,
        RuleStatus.ACTIVE, effective, expireAt,
        createdBy, createdBy, now, now, Version.initial());
    rule.registerDomainEvent(PermissionRuleCreatedEvent.of(
        id, ruleCode, subjectType, subjectId, businessCode, overrideMode, priority, createdBy));
    return rule;
  }

  /**
   * 工厂方法:从数据库重建聚合。
   */
  public static PermissionRule reconstitute(PermissionRuleId id, String ruleCode, String ruleName,
                                            SubjectType subjectType, String subjectId,
                                            BusinessCode businessCode, Set<Action> allowedActions,
                                            boolean inheritToChildren, OverrideMode overrideMode,
                                            Integer priority,
                                            RuleStatus status,
                                            LocalDateTime effectiveAt, LocalDateTime expireAt,
                                            UserNo createdBy, UserNo updatedBy,
                                            LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    return new PermissionRule(id, ruleCode, ruleName, subjectType, subjectId,
        businessCode, allowedActions, inheritToChildren, overrideMode, priority,
        status, effectiveAt, expireAt,
        createdBy, updatedBy, createdAt, updatedAt, version);
  }

  /**
   * 禁用规则。
   *
   * <p>仅 ACTIVE 状态可禁用。
   *
   * @param operator 操作人
   */
  public void disable(UserNo operator) {
    if (!status.canDisable()) {
      throw new DomainException(IamAuthzErrorCode.PERMISSION_RULE_STATUS_INVALID)
          .withUserDetail("当前状态不允许禁用: " + status)
          .withContext("currentStatus", status.name())
          .withContext("targetStatus", RuleStatus.DISABLED.name());
    }
    this.status = RuleStatus.DISABLED;
    markUpdated(operator);
    registerDomainEvent(PermissionRuleDisabledEvent.of(id(), ruleCode, operator));
  }

  /**
   * 启用规则。
   *
   * <p>仅 DISABLED 状态可启用。
   *
   * @param operator 操作人
   */
  public void enable(UserNo operator) {
    if (!status.canEnable()) {
      throw new DomainException(IamAuthzErrorCode.PERMISSION_RULE_STATUS_INVALID)
          .withUserDetail("当前状态不允许启用: " + status)
          .withContext("currentStatus", status.name())
          .withContext("targetStatus", RuleStatus.ACTIVE.name());
    }
    this.status = RuleStatus.ACTIVE;
    markUpdated(operator);
    registerDomainEvent(PermissionRuleEnabledEvent.of(id(), ruleCode, operator));
  }

  /**
   * 判断规则在指定时刻是否生效。
   *
   * <p>生效条件:状态为 ACTIVE,且 moment 在 [effectiveAt, expireAt) 区间内。
   *
   * @param moment 判断时刻(可空,空表示当前时刻)
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
   * 判断规则是否适用于指定匹配上下文。
   *
   * <p>匹配逻辑:按 subjectType 从 context 取对应维度值,与 subjectId 比较。
   * 本方法仅检查维度匹配,不检查规则生效状态;调用方需先调用 {@link #isEffectiveAt} 判断。
   *
   * @param context 匹配上下文
   * @return 匹配返回 true
   */
  public boolean matches(PermissionMatchContext context) {
    Objects.requireNonNull(context, "context cannot be null");
    String dimensionValue = context.subjectIdFor(subjectType);
    return subjectId.equals(dimensionValue);
  }

  /**
   * 生成本规则对应的权限码集合。
   *
   * <p>权限码格式:businessCode + "." + action(如 "ANNUITY_ESTABLISH.HANDLE")。
   * 此方法在 PermissionResolver 计算权限时被调用。
   *
   * @return 权限码集合(不可变)
   */
  public Set<PermissionCode> permissionCodes() {
    return allowedActions.stream()
        .map(businessCode::toPermissionCode)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * 返回规则的有效优先级。
   *
   * <p>若显式设置了 priority,则使用显式值;否则使用 {@link SubjectType#priority()}。
   *
   * @return 优先级数值(越大越高)
   */
  public int effectivePriority() {
    return priority != null ? priority : subjectType.priority();
  }

  public String ruleCode() { return ruleCode; }
  public String ruleName() { return ruleName; }
  public SubjectType subjectType() { return subjectType; }
  public String subjectId() { return subjectId; }
  public BusinessCode businessCode() { return businessCode; }
  public Set<Action> allowedActions() { return Set.copyOf(allowedActions); }
  public boolean isInheritToChildren() { return inheritToChildren; }
  public OverrideMode overrideMode() { return overrideMode; }
  public Integer priority() { return priority; }
  public RuleStatus status() { return status; }
  public LocalDateTime effectiveAt() { return effectiveAt; }
  public LocalDateTime expireAt() { return expireAt; }

  @Override
  protected void validateInvariants() {
    if (ruleCode == null || ruleCode.isBlank()) {
      throw new IllegalStateException("PermissionRule.ruleCode cannot be null or blank");
    }
    if (ruleName == null || ruleName.isBlank()) {
      throw new IllegalStateException("PermissionRule.ruleName cannot be null or blank");
    }
    if (subjectType == null) {
      throw new IllegalStateException("PermissionRule.subjectType cannot be null");
    }
    if (subjectId == null || subjectId.isBlank()) {
      throw new IllegalStateException("PermissionRule.subjectId cannot be null or blank");
    }
    if (businessCode == null) {
      throw new IllegalStateException("PermissionRule.businessCode cannot be null");
    }
    if (allowedActions == null || allowedActions.isEmpty()) {
      throw new IllegalStateException("PermissionRule.allowedActions cannot be null or empty");
    }
    if (overrideMode == null) {
      throw new IllegalStateException("PermissionRule.overrideMode cannot be null");
    }
    if (status == null) {
      throw new IllegalStateException("PermissionRule.status cannot be null");
    }
    if (effectiveAt == null) {
      throw new IllegalStateException("PermissionRule.effectiveAt cannot be null");
    }
    if (expireAt != null && !expireAt.isAfter(effectiveAt)) {
      throw new IllegalStateException("PermissionRule.expireAt must be after effectiveAt");
    }
    if (priority != null && priority < 0) {
      throw new IllegalStateException("PermissionRule.priority cannot be negative");
    }
    if (inheritToChildren && subjectType != SubjectType.CUSTOMER) {
      throw new IllegalStateException(
          "PermissionRule.inheritToChildren only meaningful for CUSTOMER subjectType");
    }
  }

  private static void validateCommon(String ruleCode, String ruleName,
                                     SubjectType subjectType, String subjectId,
                                     BusinessCode businessCode,
                                     Set<Action> allowedActions,
                                     OverrideMode overrideMode,
                                     LocalDateTime effectiveAt, LocalDateTime expireAt) {
    if (ruleCode == null || ruleCode.isBlank()) {
      throw new DomainException(IamAuthzErrorCode.PERMISSION_RULE_CODE_DUPLICATE)
          .withUserDetail("规则编码不能为空");
    }
    if (ruleName == null || ruleName.isBlank()) {
      throw new DomainException(IamAuthzErrorCode.PERMISSION_RULE_NOT_FOUND)
          .withUserDetail("规则名称不能为空");
    }
    Objects.requireNonNull(subjectType, "subjectType cannot be null");
    if (subjectId == null || subjectId.isBlank()) {
      throw new DomainException(IamAuthzErrorCode.SUBJECT_ID_REQUIRED)
          .withUserDetail("主体标识不能为空");
    }
    Objects.requireNonNull(businessCode, "businessCode cannot be null");
    if (allowedActions == null || allowedActions.isEmpty()) {
      throw new DomainException(IamAuthzErrorCode.ACTION_EMPTY)
          .withUserDetail("动作集合不能为空");
    }
    Objects.requireNonNull(overrideMode, "overrideMode cannot be null");
    if (expireAt != null && effectiveAt != null && !expireAt.isAfter(effectiveAt)) {
      throw new DomainException(IamAuthzErrorCode.RULE_EFFECTIVE_PERIOD_INVALID)
          .withUserDetail("失效时间必须晚于生效时间");
    }
  }

  private static Set<Action> copyActions(Set<Action> source) {
    return source == null ? new HashSet<>() : new HashSet<>(source);
  }
}
