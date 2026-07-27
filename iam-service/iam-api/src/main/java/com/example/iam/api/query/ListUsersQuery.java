package com.example.iam.api.query;

import com.example.shared.web.core.dto.PageQuery;
import jakarta.validation.constraints.NotNull;

/**
 * 用户列表查询
 *
 * <p>支持按渠道、登录名模糊匹配、状态等条件分页查询用户列表。
 *
 * @author iam-service
 */
public record ListUsersQuery(
    /**
     * 渠道类型(可选,按渠道过滤,如 INTERNET/HQ/BRANCH)
     */
    String channelType,
    /**
     * 登录名(可选,模糊匹配)
     */
    String loginName,
    /**
     * 用户状态(可选,如 ACTIVE/DISABLED/LOCKED)
     */
    String status,
    /**
     * 分页参数
     */
    @NotNull(message = "分页参数不能为空")
    PageQuery pageQuery
) {
}
