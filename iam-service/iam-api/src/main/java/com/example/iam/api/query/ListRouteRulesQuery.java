package com.example.iam.api.query;

import jakarta.validation.constraints.NotNull;

import com.example.shared.web.core.dto.PageQuery;

/**
 * 路由规则列表查询
 *
 * <p>按路由匹配模式(模糊)、检查类型、启用状态等条件分页查询路由规则列表。
 *
 * @author iam-service
 */
public record ListRouteRulesQuery(
    /**
     * 路由匹配模式(可空,模糊匹配)
     */
    String routePattern,
    /**
     * 检查类型(可空,LOGIN/PERMISSION/ROLE/CHANNEL/SKIP)
     */
    String checkType,
    /**
     * 是否启用(可空,true=仅启用,false=仅禁用,不传=全部)
     */
    Boolean enabled,
    /**
     * 分页参数
     */
    @NotNull(message = "分页参数不能为空")
    PageQuery pageQuery
) {
}
