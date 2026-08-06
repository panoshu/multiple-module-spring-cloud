package com.pension.permission.domain.assignment.aggregate;


import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.enumeration.AssignmentStatus;
import com.pension.permission.domain.assignment.event.AssignmentCreated;
import com.pension.permission.domain.assignment.event.AssignmentDeactivated;
import com.pension.permission.domain.assignment.event.AssignmentRoleChanged;
import com.pension.permission.types.AssignmentId;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;

import java.time.LocalDateTime;

/**
 * 账号在某个范围内被赋予某个角色。范围可以锚定在具体计划、客户名下全部计划、
 * 或产品名下全部计划——这也是原先"PlanMember"这个名字容易产生误导的地方，
 * 它其实不一定绑定单个计划。
 * changeRole/deactivate 是包内可见方法：只允许通过同包的 GrantProvisioningService
 * 来调用，从而保证"角色一变、Grant必跟着重新生成"这条不变量不会被绕过。
 */
public class AgentIdentityAssignment extends AggregateRoot<AssignmentId> {
  public final AssignmentScopeDimension scopeDimension;
  public final String scopeValue;
  private final UserNo userNo;
  /**
   * 仅CUSTOMER维度有意义：是否级联到该客户的下级客户
   */
  private final boolean inheritable;
  public RoleCode roleCode;
  private AssignmentStatus status;

  /**
   * ===============================
   * 1. 业务创建构造方法
   * ===============================
   * <p>
   * 新建聚合实例使用。
   */
  private AgentIdentityAssignment(
    AssignmentId id,
    UserNo creator,
    UserNo userNo,
    RoleCode roleCode,
    AssignmentScopeDimension scopeDimension,
    String scopeValue,
    boolean inheritable
  ) {
    super(id, creator);

    this.userNo = userNo;
    this.roleCode = roleCode;
    this.scopeDimension = scopeDimension;
    this.scopeValue = scopeValue;
    this.inheritable = inheritable;
    this.status = AssignmentStatus.ACTIVE;

    validateInvariants();

    // 新建聚合才产生创建事件
    this.registerDomainEvent(AssignmentCreated.of(this.id(), userNo));
  }


  /**
   * ===============================
   * 2. 数据库重建构造方法
   * ===============================
   * Repository 专用。
   * 不产生领域事件。
   */
  private AgentIdentityAssignment(
    AssignmentId id,
    UserNo createdBy,
    UserNo updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Version version,

    UserNo userNo,
    RoleCode roleCode,
    AssignmentScopeDimension scopeDimension,
    String scopeValue,
    boolean inheritable,
    AssignmentStatus status
  ) {
    super(
      id,
      createdBy,
      updatedBy,
      createdAt,
      updatedAt,
      version
    );

    this.userNo = userNo;
    this.roleCode = roleCode;
    this.scopeDimension = scopeDimension;
    this.scopeValue = scopeValue;
    this.inheritable = inheritable;
    this.status = status;

    validateInvariants();
  }


  /**
   * ===============================
   * 工厂方法
   * ===============================
   */

  public static AgentIdentityAssignment create(
    AssignmentId id,
    UserNo creator,
    UserNo userNo,
    RoleCode roleCode,
    AssignmentScopeDimension scopeDimension,
    String scopeValue,
    boolean inheritable
  ) {

    return new AgentIdentityAssignment(
      id,
      creator,
      userNo,
      roleCode,
      scopeDimension,
      scopeValue,
      inheritable
    );
  }


  /**
   * Repository 重建入口。
   * 注意：
   * 这个方法不应该暴露给业务层。
   */
  public static AgentIdentityAssignment reconstitute(
    AssignmentId id,
    UserNo createdBy,
    UserNo updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Version version,

    UserNo userNo,
    RoleCode roleCode,
    AssignmentScopeDimension scopeDimension,
    String scopeValue,
    boolean inheritable,
    AssignmentStatus status
  ) {

    return new AgentIdentityAssignment(
      id,
      createdBy,
      updatedBy,
      createdAt,
      updatedAt,
      version,
      userNo,
      roleCode,
      scopeDimension,
      scopeValue,
      inheritable,
      status
    );
  }


  public void changeRole(RoleCode newRoleCode) {

    if (newRoleCode == null) {
      throw new IllegalArgumentException("RoleCode cannot be null.");
    }

    if (this.roleCode.equals(newRoleCode)) {
      return;
    }

    this.roleCode = newRoleCode;

    registerDomainEvent(
      AssignmentRoleChanged.of(
        this.id(),
        newRoleCode,
        userNo
      )
    );
  }

  public void deactivate() {

    if (this.status == AssignmentStatus.DEACTIVATED) {
      return;
    }

    this.status = AssignmentStatus.DEACTIVATED;

    registerDomainEvent(
      AssignmentDeactivated.of(
        this.id(),
        userNo
      )
    );
  }


  public boolean isActive() {
    return status == AssignmentStatus.ACTIVE;
  }

  @Override
  protected void validateInvariants() {

    if (userNo == null) {
      throw new IllegalStateException("Assignment user cannot be null.");
    }

    if (roleCode == null) {
      throw new IllegalStateException("Assignment role cannot be null.");
    }

    if (scopeDimension == null) {
      throw new IllegalStateException("Assignment scope dimension cannot be null.");
    }

    if (scopeValue == null || scopeValue.isBlank()) {
      throw new IllegalStateException("Assignment scope value cannot be blank.");
    }

    if (status == null) {
      throw new IllegalStateException("Assignment status cannot be null.");
    }


    /*
     * 领域规则：
     *
     * inheritable 只有 CUSTOMER 维度有效
     */
    if (inheritable
      && scopeDimension != AssignmentScopeDimension.CUSTOMER) {

      throw new IllegalStateException(
        "Only CUSTOMER scope supports inheritance."
      );
    }
  }

  public AssignmentScopeDimension scopeDimension() {
    return this.scopeDimension;
  }

  public String scopeValue() {
    return this.scopeValue;
  }

  public RoleCode roleCode() {
    return this.roleCode;
  }

  public boolean isInheritable() {
    return this.inheritable;
  }

  public UserNo userNo() {
    return this.userNo;
  }
}
