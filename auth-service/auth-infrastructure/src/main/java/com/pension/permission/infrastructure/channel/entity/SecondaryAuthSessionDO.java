package com.pension.permission.infrastructure.channel.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 二次授权会话DO实体
 *
 * @author auth-service
 */
@Data
@Table("t_auth_secondary_auth_session")
public class SecondaryAuthSessionDO {

  /**
   * 二次授权会话ID
   */
  @Id(keyType = KeyType.None)
  private String id;

  /**
   * 柜员账号ID
   */
  private String tellerAccountId;

  /**
   * 经办人账号ID
   */
  private String approverAccountId;

  /**
   * 凭证持有者类型
   */
  private String credentialOwnerType;

  /**
   * 凭证持有者ID
   */
  private String credentialOwnerId;

  /**
   * 经办人手机号
   */
  private String approverMobile;

  /**
   * 目标计划ID
   */
  private String planId;

  /**
   * BCrypt哈希验证码
   */
  private String verificationCodeHash;

  /**
   * 验证码发送时间
   */
  private LocalDateTime verificationSentAt;

  /**
   * 验证码过期时间
   */
  private LocalDateTime verificationExpiresAt;

  /**
   * 验证码剩余次数
   */
  private Integer verificationRemaining;

  /**
   * 有效身份-经办ID
   */
  private String effectiveIdentityId;

  /**
   * 有效身份-柜员ID
   */
  private String effectiveIdentityActing;

  /**
   * 是否经二次授权
   */
  private Boolean effectiveViaSecondary;

  /**
   * 权限快照JSON
   */
  private String snapshotPermissions;

  /**
   * 快照冻结时间
   */
  private LocalDateTime snapshotFrozenAt;

  /**
   * 快照TTL过期时间
   */
  private LocalDateTime snapshotExpiresAt;

  /**
   * 状态
   */
  private String status;

  /**
   * 发起时间
   */
  private LocalDateTime initiatedAt;

  /**
   * 待授权超时时间
   */
  private LocalDateTime pendingExpiresAt;

  /**
   * 授权时间
   */
  private LocalDateTime authorizedAt;

  /**
   * 会话过期时间
   */
  private LocalDateTime expiresAt;

  /**
   * 撤销原因
   */
  private String revokeReason;

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
