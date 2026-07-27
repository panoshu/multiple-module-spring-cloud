package com.example.iam.api.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建路由规则命令
 *
 * <p>创建一条请求路由规则,定义指定路径模式的访问检查策略
 * (LOGIN/PERMISSION/ROLE/CHANNEL/SKIP),用于网关或拦截器层的统一鉴权。
 *
 * @author iam-service
 */
public record CreateRouteRuleCommand(
    /**
     * 路由匹配模式(Ant 风格,如 /api/order/**)
     */
    @NotBlank(message = "路由匹配模式不能为空")
    String routePattern,
    /**
     * 检查类型(LOGIN/PERMISSION/ROLE/CHANNEL/SKIP)
     */
    @NotBlank(message = "检查类型不能为空")
    String checkType,
    /**
     * 检查值(可空,SKIP 类型时为空)
     */
    String checkValue,
    /**
     * 规则描述(可空)
     */
    String description,
    /**
     * 优先级(0-999,数字越小优先级越高,默认 0)
     */
    @Min(value = 0, message = "优先级最小值为 0")
    @Max(value = 999, message = "优先级最大值为 999")
    int priority,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
}
