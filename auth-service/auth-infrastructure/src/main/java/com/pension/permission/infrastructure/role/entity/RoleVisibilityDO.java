package com.pension.permission.infrastructure.role.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色可见性范围DO实体
 *
 * <p>对应值对象 {@code RoleVisibilityScope}，按 (dimension, scope_value) 做 upsert。
 * 主键为数据库自增，不使用 ULID。</p>
 *
 * @author auth-service
 */
@Data
@Table("t_auth_role_visibility")
public class RoleVisibilityDO {

  /**
   * 自增主键
   */
  @Id(keyType = KeyType.Auto)
  private Long id;

  /**
   * 维度: PLAN/CUSTOMER
   */
  private String dimension;

  /**
   * 范围值（PLAN 维度存 PlanNo.value()，CUSTOMER 维度存 CustomerNo.value()）
   */
  private String scopeValue;

  /**
   * 可见性模式: SHOW_ALL/EXCLUSIVE_ONLY
   */
  private String mode;

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
