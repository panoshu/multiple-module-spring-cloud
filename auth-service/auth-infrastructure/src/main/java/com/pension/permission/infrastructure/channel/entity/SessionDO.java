package com.pension.permission.infrastructure.channel.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道会话数据载体（Redis JSON 序列化用）.
 *
 * <p>原为 MyBatis-Flex DO，自 SessionRepository 改为 Redis 实现后，
 * 本类不再映射数据库表，仅作为 {@link com.pension.permission.domain.channel.aggregate.Session}
 * 聚合根与 Redis JSON 之间的序列化载体。</p>
 *
 * <p>字段含义保持与原 DO 一致，便于 {@code SessionConverter} 复用现有转换逻辑。</p>
 */
@Data
public class SessionDO {

  /**
   * 会话ID（= Sa-Token tokenValue）
   */
  private String id;

  /**
   * 主账号ID
   */
  private String primaryAccountId;

  /**
   * 渠道
   */
  private String channel;

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
   * 二次授权会话ID
   */
  private String secondaryAuthSessionId;

  /**
   * 已选计划ID
   */
  private String selectedPlanId;

  /**
   * 会话过期时间
   */
  private LocalDateTime expiresAt;

  /**
   * 会话状态
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
   * 创建时间（由应用层通过 Converter 从领域对象映射）
   */
  private LocalDateTime createTime;

  /**
   * 更新时间（由应用层通过 Converter 从领域对象映射）
   */
  private LocalDateTime updateTime;

  /**
   * 删除标记（Redis 实现下保留字段以兼容旧转换逻辑，实际不使用）
   */
  private Boolean deleted;

  /**
   * 乐观锁版本号（Redis 实现下保留字段以兼容旧转换逻辑，实际不使用）
   */
  private Integer version;
}
