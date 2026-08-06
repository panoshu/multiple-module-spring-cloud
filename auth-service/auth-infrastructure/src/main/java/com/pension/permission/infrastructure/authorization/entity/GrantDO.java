package com.pension.permission.infrastructure.authorization.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 授权策略主记录 DO 实体.
 *
 * <p>承载 {@link com.pension.permission.domain.authorization.aggregate.Grant} 聚合根的持久化。
 * 复杂值对象集合（subject、scopeRules、permissions）以 JSON 字段存储。</p>
 *
 * <p>subject 字段是 sealed interface {@code GrantSubject} 的 JSON 序列化结果，
 * 序列化时通过 Jackson {@code @JsonTypeInfo} + {@code @JsonSubTypes} 保留类型信息，
 * 详见 {@link com.pension.permission.infrastructure.authorization.converter.GrantConverter}。</p>
 */
@Data
@Table("t_auth_grant")
public class GrantDO {

  /**
   * Grant ID (ULID)
   */
  @Id(keyType = KeyType.None)
  private String id;

  /**
   * 授权主体 JSON（包含类型信息，区分 CapabilitySubject/UserListSubject/PlanAllMembersSubject/PlanRoleSubject）
   */
  private String subject;

  /**
   * 范围规则集合 JSON（ScopeRule 数组）
   */
  private String scopeRules;

  /**
   * 权限集合 JSON（Permission 数组）
   */
  private String permissions;

  /**
   * 授权类型: BASE/DELEGATE_WHOLESALE/DELEGATE_SELECTIVE
   */
  private String grantType;

  /**
   * 授权来源: HQ_CONFIG/PLAN_DELEGATE/CUSTOMER_TO_AGENT/ROLE_TEMPLATE
   */
  private String origin;

  /**
   * 效果: ALLOW/DENY
   */
  private String effect;

  /**
   * 代办场景下：授权方计划编号；非代办场景为 null
   */
  private String sourcePlanNo;

  /**
   * 代办场景下：接受方（目标）计划编号；非代办场景为 null
   */
  private String targetPlanNo;

  /**
   * 状态: DRAFT/PENDING_APPROVAL/EFFECTIVE/REJECTED/REVOKED
   */
  private String status;

  /**
   * 有效期开始时间
   */
  private LocalDateTime validityStart;

  /**
   * 有效期结束时间（null 表示长期有效）
   */
  private LocalDateTime validityEnd;

  /**
   * 创建人
   */
  private String createdBy;

  /**
   * 更新人
   */
  private String updatedBy;

  /**
   * 创建时间（由应用层通过 Converter 从领域对象映射，不使用 ORM 自动管理）
   */
  private LocalDateTime createTime;

  /**
   * 更新时间（由应用层通过 Converter 从领域对象映射，不使用 ORM 自动管理）
   */
  private LocalDateTime updateTime;

  /**
   * 删除标记
   */
  @Column(isLogicDelete = true)
  private Boolean deleted;

  /**
   * 乐观锁版本号
   */
  @Column(version = true)
  private Integer version;
}
