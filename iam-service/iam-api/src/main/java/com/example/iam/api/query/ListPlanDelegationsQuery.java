package com.example.iam.api.query;

import jakarta.validation.constraints.NotNull;

import com.example.shared.web.core.dto.PageQuery;

/**
 * 计划代办列表查询
 *
 * <p>按授权方计划编号、被授权方计划编号、代办类型、状态等条件分页查询计划代办关系列表。
 *
 * @author iam-service
 */
public record ListPlanDelegationsQuery(
    /**
     * 授权方计划编号(可空,精确匹配)
     */
    String delegatorPlanNo,
    /**
     * 被授权方计划编号(可空,精确匹配)
     */
    String delegateePlanNo,
    /**
     * 代办类型(可空,ALL_OPERATORS/SPECIFIC_OPERATORS)
     */
    String delegationType,
    /**
     * 代办状态(可空,如 ACTIVE/REVOKED/EXPIRED)
     */
    String status,
    /**
     * 分页参数
     */
    @NotNull(message = "分页参数不能为空")
    PageQuery pageQuery
) {
}
