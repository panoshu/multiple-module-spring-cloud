package com.pension.permission.infrastructure.role.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色权限模板DO实体
 *
 * @author auth-service
 */
@Data
@Table("t_auth_role_template")
public class RoleTemplateDO {

  /**
   * 模板ID
   */
  @Id(keyType = KeyType.None)
  private String id;

  /**
   * 角色编码
   */
  private String roleCode;

  /**
   * 作用域维度
   */
  private String scopeDimension;

  /**
   * 作用域值（GLOBAL 时为 null）
   */
  private String scopeValue;

  /**
   * 权限集合JSON
   */
  private String permissions;

  /**
   * 模板状态
   */
  private String status;

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
