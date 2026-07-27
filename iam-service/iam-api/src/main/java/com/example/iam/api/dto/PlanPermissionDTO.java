package com.example.iam.api.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 当前计划与权限DTO
 *
 * <p>返回当前用户已选定的计划及其权限码集合,以及二次授权相关信息(仅网点渠道)。
 *
 * @author iam-service
 */
public record PlanPermissionDTO(
    /**
     * 计划编号
     */
    String planId,
    /**
     * 计划名称
     */
    String planName,
    /**
     * 客户编号
     */
    String customerNo,
    /**
     * 权限码集合(如 {"business1.handle", "business2.query"})
     */
    Set<String> permissions,
    /**
     * 计划选定时间
     */
    LocalDateTime selectedAt,
    /**
     * 网点渠道二次授权会话ID(其他渠道为空)
     */
    Long secondaryAuthSessionId,
    /**
     * 网点渠道借用的经办人ID(其他渠道为空)
     */
    Long borrowedApproverId
) {
}
