package com.example.auth.api.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询父计划请求（立项批次 66）。
 * <p>
 * 对应 {@code LEVEL_UP} 审批节点：取 {@code PlanSnapshot.parentPlanNo}（直接父计划），
 * 整条 {@code ancestorsOf} 继承链不在本立项范围内。
 *
 * @param planNo 计划号
 * @author auth-api
 */
public record GetParentPlanRequest(
  @NotBlank(message = "计划号不能为空")
  String planNo
) {
}
