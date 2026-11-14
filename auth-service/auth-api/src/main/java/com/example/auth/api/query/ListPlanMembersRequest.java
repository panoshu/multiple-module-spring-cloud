package com.example.auth.api.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询计划成员请求（立项批次 66）。
 * <p>
 * 数据源为 auth {@code t_auth_assignment} 的 PLAN 精确 scope，并按组织目录的
 * 计划层级做 CUSTOMER / PRODUCT 继承匹配（见 {@code PlanReachabilityService}）。
 * {@code excludeUserNo} 用于 SAME_PLAN 节点排除发起人本人，由消费侧传入，可选。
 *
 * @param planNo       计划号
 * @param excludeUserNo 需排除的用户编号（可空）
 * @author auth-api
 */
public record ListPlanMembersRequest(
  @NotBlank(message = "计划号不能为空")
  String planNo,

  String excludeUserNo
) {
}
