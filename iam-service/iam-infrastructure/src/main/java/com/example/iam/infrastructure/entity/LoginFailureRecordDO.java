package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录失败记录 DO。
 *
 * <p>对应表 {@code t_iam_login_failure_record},作为 {@link LoginLogDO} 的子表,
 * 通过 {@code loginLogId} 关联。一次登录尝试可关联多条失败记录(如密码错误 + IP 黑名单)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_login_failure_record")
public class LoginFailureRecordDO {

    @Id(keyType = KeyType.None)
    private Long id;

    /** 关联登录日志 ID(FK to t_iam_login_log.id) */
    private Long loginLogId;

    /** 失败原因代码(如 WRONG_PASSWORD/USER_NOT_FOUND) */
    private String reason;

    /** 人类可读详情(可空) */
    private String detail;

    /** 失败时间 */
    private LocalDateTime failureTime;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
