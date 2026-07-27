package com.example.iam.api.dto;

import java.time.LocalDateTime;

/**
 * 用户DTO
 *
 * <p>对应用户聚合根(User)的展示视图,聚合三渠道统一的用户身份与状态信息。
 *
 * @author iam-service
 */
public record UserDTO(
    /**
     * 用户ID
     */
    Long userId,
    /**
     * 渠道类型(INTERNET/HQ/BRANCH)
     */
    String channelType,
    /**
     * 登录名
     */
    String loginName,
    /**
     * 显示名称
     */
    String displayName,
    /**
     * 用户状态(ACTIVE/DISABLED/LOCKED)
     */
    String status,
    /**
     * 最后登录时间
     */
    LocalDateTime lastLoginTime,
    /**
     * 最后登录IP
     */
    String lastLoginIp,
    /**
     * 用户渠道档案
     */
    UserProfileDTO profile,
    /**
     * 创建时间
     */
    LocalDateTime createdAt,
    /**
     * 更新时间
     */
    LocalDateTime updatedAt,
    /**
     * 乐观锁版本号
     */
    Long version
) {
}
