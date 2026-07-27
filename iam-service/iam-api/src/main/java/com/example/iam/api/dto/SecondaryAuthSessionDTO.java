package com.example.iam.api.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 二次授权会话DTO
 *
 * <p>对应二次授权会话聚合根(SecondaryAuthSession)的展示视图,用于网点渠道柜员借用经办人权限的场景。
 *
 * @author iam-service
 */
public record SecondaryAuthSessionDTO(
    /**
     * 会话ID
     */
    Long sessionId,
    /**
     * 柜员用户ID
     */
    Long tellerId,
    /**
     * 经办人用户ID
     */
    Long approverId,
    /**
     * 客户编号(外部系统)
     */
    String customerNo,
    /**
     * 计划编号(外部系统)
     */
    String planId,
    /**
     * 权限快照(权限码字符串集合,经办人授权时冻结)
     */
    Set<String> permissionSnapshot,
    /**
     * 会话状态(PENDING/AUTHORIZED/REJECTED/EXPIRED/REVOKED/CLOSED)
     */
    String status,
    /**
     * 发起时间
     */
    LocalDateTime initiatedAt,
    /**
     * 授权时间(经办人确认时设置)
     */
    LocalDateTime authorizedAt,
    /**
     * 过期时间
     */
    LocalDateTime expireAt,
    /**
     * 撤销原因(可空)
     */
    String revokeReason,
    /**
     * 创建时间
     */
    LocalDateTime createdAt,
    /**
     * 更新时间
     */
    LocalDateTime updatedAt
) {
}
