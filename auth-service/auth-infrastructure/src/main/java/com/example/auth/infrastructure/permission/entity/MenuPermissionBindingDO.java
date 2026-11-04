package com.example.auth.infrastructure.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单-权限绑定 DO 实体.
 *
 * @author auth-service
 */
@Data
@Table("t_auth_menu_permission_binding")
public class MenuPermissionBindingDO {

  @Id(keyType = KeyType.None)
  private String id;

  private String menuId;

  private String businessCode;

  private String actionCode;

  private String createdBy;

  private String updatedBy;

  private LocalDateTime createTime;

  private LocalDateTime updateTime;

  private Boolean deleted;

  private Integer version;
}
