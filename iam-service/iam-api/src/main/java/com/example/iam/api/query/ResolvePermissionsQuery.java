package com.example.iam.api.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 解析用户权限快照查询
 *
 * <p>用于调试或预览:解析指定用户在指定计划下的最终权限快照,
 * 包含经规则合并、覆盖、继承计算后的可执行业务动作集合。
 *
 * @author iam-service
 */
public record ResolvePermissionsQuery(
    /**
     * 用户 ID
     */
    @NotNull(message = "用户ID不能为空")
    Long userId,
    /**
     * 计划编号
     */
    @NotBlank(message = "计划编号不能为空")
    String planId
) {
}
