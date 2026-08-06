package com.pension.permission.infrastructure.user.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户DO实体
 *
 * @author auth-service
 */
@Data
@Table("t_auth_user")
public class UserDO {

  /**
   * 用户ID
   */
  @Id(keyType = KeyType.None)
  private String id;

  /**
   * 用户类型（存储 UserType.name()）
   */
  private String userType;

  /**
   * 证件类型（存储 IdentityType.name()）
   */
  private String identityType;

  /**
   * 证件号码
   */
  private String identityNumber;

  /**
   * 手机号
   */
  private String mobile;

  /**
   * 邮箱
   */
  private String email;

  /**
   * 固定电话-区号
   */
  private String telephoneAreaCode;

  /**
   * 固定电话-号码
   */
  private String telephoneNumber;

  /**
   * 固定电话-分机号
   */
  private String telephoneExtension;

  /**
   * 地址-国家
   */
  private String addressCountry;

  /**
   * 地址-省
   */
  private String addressProvince;

  /**
   * 地址-市
   */
  private String addressCity;

  /**
   * 地址-区
   */
  private String addressDistrict;

  /**
   * 地址-详情
   */
  private String addressDetail;

  /**
   * 邮政编码
   */
  private String postalCode;

  /**
   * 用户状态（存储 UserStatus.name()）
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
