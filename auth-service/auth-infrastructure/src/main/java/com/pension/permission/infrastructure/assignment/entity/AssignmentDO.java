package com.pension.permission.infrastructure.assignment.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号身份分配DO实体
 *
 * @author auth-service
 */
@Data
@Table("t_auth_assignment")
public class AssignmentDO {

  /**
   * 分配ID
   */
  @Id(keyType = KeyType.None)
  private String id;

  /**
   * 被分配账号
   */
  private String userNo;

  /**
   * 角色编码
   */
  private String roleCode;

  /**
   * 范围维度
   */
  private String scopeDimension;

  /**
   * 范围值
   */
  private String scopeValue;

  /**
   * 是否可继承（仅 CUSTOMER 维度有意义）
   */
  private Boolean inheritable;

  /**
   * 分配状态
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
