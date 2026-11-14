package com.example.auth.api;

import com.example.auth.api.dto.ParentPlanResponse;
import com.example.auth.api.dto.PlanMembersResponse;
import com.example.auth.api.query.GetParentPlanRequest;
import com.example.auth.api.query.ListPlanMembersRequest;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 组织目录查询 API（立项批次 66）。
 * <p>
 * 供 approval-service 解析 {@code SAME_PLAN} / {@code LEVEL_UP} 审批节点使用：
 * {@code /members} 取同一计划范围内的有效用户，{@code /parent} 取直接父计划号。
 * 服务端按 {@code @RequirePermission} 保护，响应只携带编号、不携带敏感字段；
 * 未知计划或组织目录不可用时 fail-closed 返回空，避免把他人任务误展开为审批人。
 *
 * @author auth-api
 */
@HttpExchange("/plan-directory")
public interface PlanDirectoryApi {

  /**
   * 查询指定计划范围内的有效用户（可排除发起人）。
   *
   * @param request 计划号 + 可选排除用户
   * @return 用户编号列表
   */
  @PostExchange("/members")
  ApiResult<PlanMembersResponse> listMembers(@RequestBody @Valid ListPlanMembersRequest request);

  /**
   * 查询计划的直接父计划号。
   *
   * @param request 计划号
   * @return 父计划号；根计划或无层级数据时为 {@code null}
   */
  @PostExchange("/parent")
  ApiResult<ParentPlanResponse> getParentPlan(@RequestBody @Valid GetParentPlanRequest request);
}
