package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IAM 用户主表 DO。
 *
 * <p>对应表 {@code t_iam_user},承载三渠道统一用户身份。软删除使用 {@code deleted} 字段;
 * 时间戳由应用层管理,不使用 ORM 自动填充。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_user")
public class UserDO {

    @Id(keyType = KeyType.None)
    private Long id;

    /** 渠道类型:INTERNET/HQ/BRANCH */
    private String channelType;

    /** 登录名(渠道内唯一) */
    private String loginName;

    /** 显示名 */
    private String displayName;

    /** 用户状态:ACTIVE/DISABLED/LOCKED */
    private String status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 最后登录 IP */
    private String lastLoginIp;

    private String createdBy;

    private String updatedBy;

    /** 创建时间(由应用层从领域对象 createdAt 映射) */
    private LocalDateTime createTime;

    /** 更新时间(由应用层从领域对象 updatedAt 映射) */
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
