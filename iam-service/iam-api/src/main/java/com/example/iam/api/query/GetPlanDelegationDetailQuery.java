package com.example.iam.api.query;

import jakarta.validation.constraints.NotNull;

/**
 * 计划代办详情查询
 *
 * <p>按代办关系 ID 查询单条计划代办关系的详细信息。
 *
 * @author iam-service
 */
public record GetPlanDelegationDetailQuery(
    /**
     * 代办关系 ID
     */
    @NotNull(message = "代办关系ID不能为空")
    Long delegationId
) {
}
