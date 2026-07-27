package com.example.iam.api.query;

import com.example.shared.web.core.dto.PageQuery;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 二次授权会话查询
 *
 * <p>支持按柜员、经办人、状态、计划编号、时间范围等条件分页查询二次授权会话。
 *
 * @author iam-service
 */
public record ListSecondaryAuthSessionsQuery(
    /**
     * 柜员用户 ID(可选,发起方)
     */
    Long tellerId,
    /**
     * 经办人用户 ID(可选,审批方)
     */
    Long approverId,
    /**
     * 会话状态(可选,如 PENDING/CONFIRMED/REJECTED/REVOKED/EXPIRED)
     */
    String status,
    /**
     * 计划编号(可选)
     */
    String planId,
    /**
     * 起始时间(可选,包含)
     */
    LocalDateTime startTime,
    /**
     * 结束时间(可选,包含)
     */
    LocalDateTime endTime,
    /**
     * 分页参数
     */
    @NotNull(message = "分页参数不能为空")
    PageQuery pageQuery
) {
}
