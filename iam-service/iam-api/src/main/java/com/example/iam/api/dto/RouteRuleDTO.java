package com.example.iam.api.dto;

import java.time.LocalDateTime;

/**
 * 路由规则DTO
 *
 * <p>对应路由规则聚合根(RouteRule)的展示视图,用于网关层动态鉴权的配置单元。
 *
 * @author iam-service
 */
public record RouteRuleDTO(
    /**
     * 规则ID
     */
    Long ruleId,
    /**
     * 路由匹配模式(Ant 风格,如 /internet/**)
     */
    String routePattern,
    /**
     * 校验类型(LOGIN/PERMISSION/ROLE/CHANNEL/SKIP)
     */
    String checkType,
    /**
     * 校验值(权限码/角色名/渠道名,SKIP 时为空)
     */
    String checkValue,
    /**
     * 规则描述(可空)
     */
    String description,
    /**
     * 优先级(数值越大优先级越高,匹配时按优先级倒序)
     */
    int priority,
    /**
     * 是否启用
     */
    boolean enabled,
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
