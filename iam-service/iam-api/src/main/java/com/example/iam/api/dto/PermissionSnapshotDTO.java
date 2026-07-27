package com.example.iam.api.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 权限快照DTO
 *
 * <p>对应权限快照值对象(PermissionSnapshot)的展示视图,冻结某用户在某计划上下文下的权限集合。
 * 用于网点渠道二次授权瞬间冻结经办人权限,以及缓存未命中时的权限计算结果。
 *
 * @author iam-service
 */
public record PermissionSnapshotDTO(
    /**
     * 用户ID
     */
    Long userId,
    /**
     * 计划编号
     */
    String planId,
    /**
     * 权限码集合(如 {"business1.handle", "business2.query"})
     */
    Set<String> permissions,
    /**
     * 计算时间戳
     */
    LocalDateTime calculatedAt
) {
}
