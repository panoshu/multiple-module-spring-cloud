package com.pension.permission.infrastructure.permission.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限点元数据 DO 实体。
 * <p>承载 {@link com.pension.permission.domain.permission.aggregate.PermissionItem} 聚合根的持久化。
 *
 * @author auth-service
 */
@Data
@Table("t_auth_permission_item")
public class PermissionItemDO {

  /**
   * 权限点ID（ULID）
   */
  @Id(keyType = KeyType.None)
  private String id;

  /**
   * 业务编码
   */
  private String businessCode;

  /**
   * 操作编码（NULL 表示整个业务）
   */
  private String actionCode;

  /**
   * 权限类别: BUSINESS/PLATFORM
   */
  private String category;

  /**
   * 来源: API/MANUAL
   */
  private String source;

  /**
   * 控制器类名
   */
  private String controller;

  /**
   * 方法名
   */
  private String method;

  /**
   * HTTP 方法
   */
  private String httpMethod;

  /**
   * 请求路径
   */
  private String path;

  /**
   * 展示名称
   */
  private String displayName;

  /**
   * 描述
   */
  private String description;

  /**
   * 分类分组
   */
  private String categoryGroup;

  /**
   * 排序序号
   */
  private Integer sortOrder;

  /**
   * 是否自动注册（被扫描器标记为 stale 时置 false）
   */
  private Boolean autoRegistered;

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
