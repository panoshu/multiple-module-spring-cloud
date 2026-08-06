package com.pension.permission.domain.role.aggregate;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension;
import com.pension.permission.domain.role.enumeration.RoleTemplateStatus;
import com.pension.permission.types.RoleCode;
import com.pension.permission.types.RoleTemplateId;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 角色权限模板：一份跟具体人、具体计划无关的静态配方，回答
 * "某个角色应该有哪些Permission"。身份分配(AgentIdentityAssignment)创建/变更时，
 * 按最匹配的一份模板把它实例化成一条Grant——模板本身不直接参与判定引擎。
 */
public class RoleTemplate extends AggregateRoot<RoleTemplateId> {

  private final RoleCode roleCode;
  private final RoleTemplateScopeDimension scopeDimension;
  /**
   * GLOBAL时为null，其余维度下是对应的计划/客户/产品ID
   */
  private final String scopeValue;
  private final Set<Permission> permissions;
  private RoleTemplateStatus status; // 非 final，支持启用/停用

  // ==========================================
  // 1. 构造方法（私有化，通过静态工厂暴露）
  // ==========================================

  /**
   * 场景1: 业务创建 (New)
   */
  private RoleTemplate(
    RoleTemplateId id,
    UserNo creator,
    RoleCode roleCode,
    RoleTemplateScopeDimension scopeDimension,
    String scopeValue,
    Set<Permission> permissions,
    RoleTemplateStatus initialStatus
  ) {
    super(id, creator);

    this.roleCode = roleCode;
    this.scopeDimension = scopeDimension;
    this.scopeValue = scopeValue;
    this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
    this.status = initialStatus;

    // 子类必须在构造函数末尾显式调用
    this.validateInvariants();
  }

  /**
   * 场景2: 从数据库重建 (Reconstitute)
   */
  private RoleTemplate(
    RoleTemplateId id,
    UserNo createdBy,
    UserNo updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Version version,
    RoleCode roleCode,
    RoleTemplateScopeDimension scopeDimension,
    String scopeValue,
    Set<Permission> permissions,
    RoleTemplateStatus status
  ) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);

    this.roleCode = roleCode;
    this.scopeDimension = scopeDimension;
    this.scopeValue = scopeValue;
    this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
    this.status = status;

    // 子类必须在构造函数末尾显式调用
    this.validateInvariants();

    // 重建不注册事件
  }

  // ==========================================
  // 2. 静态工厂方法
  // ==========================================

  public static RoleTemplate create(
    RoleTemplateId id,
    UserNo creator,
    RoleCode roleCode,
    RoleTemplateScopeDimension scopeDimension,
    String scopeValue,
    Set<Permission> permissions,
    RoleTemplateStatus initialStatus
  ) {
    return new RoleTemplate(id, creator, roleCode, scopeDimension, scopeValue, permissions, initialStatus);
  }

  public static RoleTemplate reconstitute(
    RoleTemplateId id,
    UserNo createdBy, UserNo updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
    RoleCode roleCode,
    RoleTemplateScopeDimension scopeDimension,
    String scopeValue,
    Set<Permission> permissions,
    RoleTemplateStatus status
  ) {
    return new RoleTemplate(id, createdBy, updatedBy, createdAt, updatedAt, version,
      roleCode, scopeDimension, scopeValue, permissions, status);
  }

  // ==========================================
  // 3. 不变性校验
  // ==========================================

  @Override
  protected void validateInvariants() {
    if (this.roleCode == null) {
      throw new IllegalArgumentException("Role code cannot be null.");
    }
    if (this.scopeDimension == null) {
      throw new IllegalArgumentException("Scope dimension cannot be null.");
    }
    if (this.status == null) {
      throw new IllegalArgumentException("Role template status cannot be null.");
    }

    // 核心业务规则：GLOBAL 维度下 scopeValue 必须为 null；非 GLOBAL 维度下 scopeValue 不能为 null
    if (this.scopeDimension == RoleTemplateScopeDimension.GLOBAL && this.scopeValue != null) {
      throw new IllegalArgumentException(
        "Scope value must be null when dimension is GLOBAL.");
    }
    if (this.scopeDimension != RoleTemplateScopeDimension.GLOBAL && this.scopeValue == null) {
      throw new IllegalArgumentException(
        "Scope value is required when dimension is " + this.scopeDimension + ".");
    }
  }

  // ==========================================
  // 4. 聚合根行为方法
  // ==========================================

  /**
   * 获取当前模板的角色编码
   */
  public RoleCode roleCode() {
    return this.roleCode;
  }

  /**
   * 获取当前模板的作用域维度
   */
  public RoleTemplateScopeDimension scopeDimension() {
    return this.scopeDimension;
  }

  /**
   * 获取当前模板的状态
   */
  public RoleTemplateStatus status() {
    return this.status;
  }

  /**
   * 获取当前模板的作用域值（GLOBAL 时返回 null）
   */
  public String scopeValue() {
    return this.scopeValue;
  }

  /**
   * 判断该模板是否为全局模板（不限定具体计划/客户/产品）
   */
  public boolean isGlobal() {
    return this.scopeDimension == RoleTemplateScopeDimension.GLOBAL;
  }

  /**
   * 判断该模板是否匹配给定的作用域。
   * 全局模板匹配任何同维度请求；非全局模板需 scopeValue 精确匹配。
   */
  public boolean matchesScope(RoleTemplateScopeDimension dimension, String value) {
    if (this.scopeDimension != dimension) {
      return false;
    }
    if (isGlobal()) {
      return true;
    }
    return Objects.equals(this.scopeValue, value);
  }

  /**
   * 获取模板持有的权限集合（不可变视图，防止外部篡改）
   */
  public Set<Permission> permissions() {
    return Collections.unmodifiableSet(this.permissions);
  }

  /**
   * 判断模板是否包含指定权限
   */
  public boolean hasPermission(Permission permission) {
    return this.permissions.contains(permission);
  }

  /**
   * 判断模板当前是否处于可用状态
   */
  public boolean isActive() {
    return this.status == RoleTemplateStatus.EFFECTIVE;
  }

  /**
   * 启用模板
   */
  public void activate(UserNo operator) {
    if (this.status == RoleTemplateStatus.EFFECTIVE) {
      return; // 幂等
    }
    this.status = RoleTemplateStatus.EFFECTIVE;
    this.markUpdated(operator);
  }

  /**
   * 停用模板
   */
  public void deactivate(UserNo operator) {
    if (this.status == RoleTemplateStatus.INACTIVE) {
      return; // 幂等
    }
    this.status = RoleTemplateStatus.INACTIVE;
    this.markUpdated(operator);
  }
}
