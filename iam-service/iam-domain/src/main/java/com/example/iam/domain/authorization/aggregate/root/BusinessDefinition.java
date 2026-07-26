package com.example.iam.domain.authorization.aggregate.root;

import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessAction;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.iam.types.BusinessDefinitionId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 业务定义聚合根 - 声明系统支持的某类业务及其支持的动作。
 *
 * <p>设计文档 3.2.3 节:业务和动作的定义本质是元数据,但出于代码组织与持久化便利,
 * 实现为聚合根(简单 CRUD,无复杂不变量)。{@code PermissionRule} 创建时通过
 * {@link #supports(Action)} 校验 allowedActions 是否在业务定义支持的动作范围内。
 *
 * <p>字段:
 * <ul>
 *   <li>{@code businessCode} - 业务编码(全局唯一,如 ANNUITY_ESTABLISH)</li>
 *   <li>{@code businessName} - 业务名称(如 "年金计划设立")</li>
 *   <li>{@code description} - 业务描述</li>
 *   <li>{@code supportedActions} - 支持的动作集合(如 HANDLE/QUERY/AUDIT)</li>
 *   <li>{@code status} - 状态(ACTIVE/DISABLED)</li>
 * </ul>
 *
 * <p>核心行为:
 * <ul>
 *   <li>{@link #create} - 创建业务定义</li>
 *   <li>{@link #supports(Action)} - 判断是否支持指定动作</li>
 *   <li>{@link #validatePermission(BusinessCode, Action)} - 校验业务编码+动作组合是否合法</li>
 *   <li>{@link #disable(UserNo)} / {@link #enable(UserNo)} - 禁用/启用</li>
 * </ul>
 *
 * <p>初始化数据见设计文档 6.8 节,通过 SQL 脚本预置。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public class BusinessDefinition extends AggregateRoot<BusinessDefinitionId> {

  private final BusinessCode businessCode;
  private final String businessName;
  private final String description;
  private final Set<BusinessAction> supportedActions;
  private boolean active;

  private BusinessDefinition(BusinessDefinitionId id, BusinessCode businessCode,
                             String businessName, String description,
                             Set<BusinessAction> supportedActions, boolean active,
                             UserNo createdBy, UserNo updatedBy,
                             LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.businessCode = businessCode;
    this.businessName = businessName;
    this.description = description;
    this.supportedActions = copyActions(supportedActions);
    this.active = active;
    this.validateInvariants();
  }

  /**
   * 工厂方法:创建新业务定义(初始为启用状态)。
   *
   * @param id               业务定义 ID
   * @param businessCode     业务编码
   * @param businessName     业务名称(非空)
   * @param description      业务描述(可空)
   * @param supportedActions 支持的动作集合(非空)
   * @param createdBy        创建人
   * @return 新建的业务定义聚合根
   */
  public static BusinessDefinition create(BusinessDefinitionId id, BusinessCode businessCode,
                                          String businessName, String description,
                                          Set<BusinessAction> supportedActions,
                                          UserNo createdBy) {
    validateCommon(businessCode, businessName, supportedActions);
    LocalDateTime now = LocalDateTime.now();
    return new BusinessDefinition(id, businessCode, businessName, description,
        supportedActions, true,
        createdBy, createdBy, now, now, Version.initial());
  }

  /**
   * 工厂方法:从数据库重建聚合。
   */
  public static BusinessDefinition reconstitute(BusinessDefinitionId id, BusinessCode businessCode,
                                                 String businessName, String description,
                                                 Set<BusinessAction> supportedActions, boolean active,
                                                 UserNo createdBy, UserNo updatedBy,
                                                 LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    return new BusinessDefinition(id, businessCode, businessName, description,
        supportedActions, active,
        createdBy, updatedBy, createdAt, updatedAt, version);
  }

  /**
   * 判断是否支持指定动作。
   *
   * @param action 业务动作
   * @return 支持返回 true
   */
  public boolean supports(Action action) {
    Objects.requireNonNull(action, "action cannot be null");
    return supportedActions.stream().anyMatch(ba -> ba.action() == action);
  }

  /**
   * 校验业务编码+动作组合是否合法(本业务定义是否支持该动作)。
   *
   * <p>校验失败抛 {@link DomainException},用于 PermissionRule 创建前的预校验。
   *
   * @param businessCode 业务编码(应与本定义的 businessCode 一致)
   * @param action       业务动作
   * @throws DomainException 当 businessCode 不匹配或动作不被支持时
   */
  public void validatePermission(BusinessCode businessCode, Action action) {
    Objects.requireNonNull(businessCode, "businessCode cannot be null");
    Objects.requireNonNull(action, "action cannot be null");
    if (!this.businessCode.equals(businessCode)) {
      throw new DomainException(IamAuthzErrorCode.BUSINESS_CODE_DUPLICATE)
          .withUserDetail("业务编码不匹配")
          .withContext("expected", this.businessCode.value())
          .withContext("actual", businessCode.value());
    }
    if (!supports(action)) {
      throw new DomainException(IamAuthzErrorCode.BUSINESS_ACTION_NOT_SUPPORTED)
          .withUserDetail("业务不支持该动作: " + action)
          .withContext("businessCode", businessCode.value())
          .withContext("action", action.name());
    }
  }

  /**
   * 禁用业务定义。
   *
   * @param operator 操作人
   */
  public void disable(UserNo operator) {
    if (!active) {
      throw new DomainException(IamAuthzErrorCode.BUSINESS_DEFINITION_STATUS_INVALID)
          .withUserDetail("业务定义已禁用,不可重复禁用");
    }
    this.active = false;
    markUpdated(operator);
  }

  /**
   * 启用业务定义。
   *
   * @param operator 操作人
   */
  public void enable(UserNo operator) {
    if (active) {
      throw new DomainException(IamAuthzErrorCode.BUSINESS_DEFINITION_STATUS_INVALID)
          .withUserDetail("业务定义已启用,不可重复启用");
    }
    this.active = true;
    markUpdated(operator);
  }

  public BusinessCode businessCode() { return businessCode; }
  public String businessName() { return businessName; }
  public String description() { return description; }
  public Set<BusinessAction> supportedActions() { return Set.copyOf(supportedActions); }
  public boolean isActive() { return active; }

  @Override
  protected void validateInvariants() {
    if (businessCode == null) {
      throw new IllegalStateException("BusinessDefinition.businessCode cannot be null");
    }
    if (businessName == null || businessName.isBlank()) {
      throw new IllegalStateException("BusinessDefinition.businessName cannot be null or blank");
    }
    if (supportedActions == null || supportedActions.isEmpty()) {
      throw new IllegalStateException("BusinessDefinition.supportedActions cannot be null or empty");
    }
  }

  private static void validateCommon(BusinessCode businessCode, String businessName,
                                     Set<BusinessAction> supportedActions) {
    Objects.requireNonNull(businessCode, "businessCode cannot be null");
    if (businessName == null || businessName.isBlank()) {
      throw new DomainException(IamAuthzErrorCode.BUSINESS_DEFINITION_NOT_FOUND)
          .withUserDetail("业务名称不能为空");
    }
    if (supportedActions == null || supportedActions.isEmpty()) {
      throw new DomainException(IamAuthzErrorCode.BUSINESS_ACTION_NOT_SUPPORTED)
          .withUserDetail("支持的动作集合不能为空");
    }
    // 校验动作不重复
    Set<Action> seen = new HashSet<>();
    for (BusinessAction ba : supportedActions) {
      if (!seen.add(ba.action())) {
        throw new DomainException(IamAuthzErrorCode.BUSINESS_ACTION_DUPLICATE)
            .withUserDetail("业务动作重复: " + ba.action())
            .withContext("action", ba.action().name());
      }
    }
  }

  private static Set<BusinessAction> copyActions(Set<BusinessAction> source) {
    return source == null ? new HashSet<>() : new HashSet<>(source);
  }
}
