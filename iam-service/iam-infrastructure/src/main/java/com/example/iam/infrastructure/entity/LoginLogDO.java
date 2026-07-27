package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志 DO。
 *
 * <p>对应表 {@code t_iam_login_log},审计每次登录尝试(成功/失败)。
 * 失败时通过 {@code t_iam_login_failure_record} 子表记录具体原因。
 * 本聚合为只读审计聚合,创建后不允许修改业务字段。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_login_log")
public class LoginLogDO {

    @Id(keyType = KeyType.None)
    private Long id;

    /** 用户 ID(可空,非用户登录场景) */
    private Long userId;

    /** 登录名 */
    private String loginName;

    /** 渠道类型 */
    private String channelType;

    /** 是否登录成功 */
    private Boolean success;

    /** 登录时间 */
    private LocalDateTime loginTime;

    /** 登录 IP */
    private String loginIp;

    /** User-Agent */
    private String userAgent;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
