package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 二次授权会话 DO。
 *
 * <p>对应表 {@code t_iam_secondary_auth_session},承载网点渠道柜员借用经办人权限的会话状态。
 * {@code permissionSnapshot} 以 JSON 字符串存储(数组形式,如 ["ANNUITY_ESTABLISH.HANDLE"])。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_secondary_auth_session")
public class SecondaryAuthSessionDO {

    @Id(keyType = KeyType.None)
    private Long id;

    /** 柜员用户 ID */
    private Long tellerId;

    /** 经办人用户 ID */
    private Long approverId;

    /** 客户编号(外部系统) */
    private String customerNo;

    /** 计划编号(外部系统) */
    private String planId;

    /** 权限快照(JSON 数组字符串,如 ["ANNUITY_ESTABLISH.HANDLE"]) */
    private String permissionSnapshot;

    /** 状态:PENDING/AUTHORIZED/REJECTED/EXPIRED/REVOKED/CLOSED */
    private String status;

    /** 发起时间 */
    private LocalDateTime initiatedAt;

    /** 授权时间 */
    private LocalDateTime authorizedAt;

    /** 过期时间 */
    private LocalDateTime expireAt;

    /** 撤销原因 */
    private String revokeReason;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
