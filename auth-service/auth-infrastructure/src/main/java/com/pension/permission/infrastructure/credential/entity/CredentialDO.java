package com.pension.permission.infrastructure.credential.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 凭证DO实体
 *
 * <p>采用单表继承策略存储 PasswordCredential 与 UKeyCredential。
 * credentialType 列区分具体子类，子类专属字段（userNo/passwordHash/keySerial）
 * 在其他类型记录中为 null。</p>
 *
 * @author auth-service
 */
@Data
@Table("t_auth_credential")
public class CredentialDO {

  /**
   * 凭证ID
   */
  @Id(keyType = KeyType.None)
  private String id;

  /**
   * 凭证类型，存储 CredentialType.name()
   */
  private String credentialType;

  /**
   * 凭证持有者类型，存储 CredentialOwner 子类的 SimpleName
   */
  private String ownerType;

  /**
   * 凭证持有者ID，存储 CredentialOwner.value()
   */
  private String ownerId;

  /**
   * 适用渠道集合，Set&lt;AnnuityChannel&gt; 序列化为 JSON 字符串
   */
  private String applicableChannels;

  /**
   * 有效期起始时间
   */
  private LocalDateTime validityStart;

  /**
   * 有效期结束时间
   */
  private LocalDateTime validityEnd;

  /**
   * 凭证状态，存储 CredentialStatus.name()
   */
  private String status;

  /**
   * 账号编号（PasswordCredential 专属）
   */
  private String userNo;

  /**
   * 密码哈希（PasswordCredential 专属）
   */
  private String passwordHash;

  /**
   * UKey 序列号（UKeyCredential 专属）
   */
  private String keySerial;

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
