package com.pension.permission.infrastructure.channel.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户渠道开通记录 DO 实体.
 *
 * <p>承载 {@link com.pension.permission.domain.channel.aggregate.CustomerChannelEntitlement}
 * 聚合根的持久化。开通的渠道集合以 JSON 数组存储（如 {@code ["NETAPP","BANK_BRANCH"]}）。</p>
 */
@Data
@Table("t_auth_customer_channel_entitlement")
public class CustomerChannelEntitlementDO {

  /**
   * 开通记录 ID (ULID)
   */
  @Id(keyType = KeyType.None)
  private String id;

  /**
   * 客户编号
   */
  private String customerNo;

  /**
   * 已开通的渠道集合 JSON（AnnuityChannel.name() 数组）
   */
  private String enabledChannels;

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
