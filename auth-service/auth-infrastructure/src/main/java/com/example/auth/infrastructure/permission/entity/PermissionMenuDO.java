package com.example.auth.infrastructure.permission.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限菜单 DO 实体.
 * <p>时间戳由应用层通过 Converter 从领域对象映射，不使用 ORM 自动管理。
 *
 * @author auth-service
 */
@Data
@Table("t_auth_permission_menu")
public class PermissionMenuDO {

  @Id(keyType = KeyType.None)
  private String id;

  private String menuCode;

  private String menuName;

  private String menuPath;

  private String parentId;

  private Integer depth;

  private Integer sortOrder;

  private String icon;

  private String route;

  private String menuCategory;

  private String visibility;

  private Boolean enabled;

  private String createdBy;

  private String updatedBy;

  private LocalDateTime createTime;

  private LocalDateTime updateTime;

  @Column(isLogicDelete = true)
  private Boolean deleted;

  @Column(version = true)
  private Integer version;
}
