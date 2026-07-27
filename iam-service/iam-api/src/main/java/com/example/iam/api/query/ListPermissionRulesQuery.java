package com.example.iam.api.query;

import jakarta.validation.constraints.NotNull;

import com.example.shared.web.core.dto.PageQuery;

/**
 * 权限规则列表查询
 *
 * <p>按规则编号、主体类型、主体标识、业务编码、状态等条件分页查询权限规则列表。
 *
 * @author iam-service
 */
public record ListPermissionRulesQuery(
    /**
     * 规则编号(可空,精确匹配)
     */
    String ruleCode,
    /**
     * 主体类型(可空,如 CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER)
     */
    String subjectType,
    /**
     * 主体标识(可空,精确匹配)
     */
    String subjectId,
    /**
     * 业务编码(可空,精确匹配)
     */
    String businessCode,
    /**
     * 规则状态(可空,如 ENABLED/DISABLED)
     */
    String status,
    /**
     * 分页参数
     */
    @NotNull(message = "分页参数不能为空")
    PageQuery pageQuery
) {
}
