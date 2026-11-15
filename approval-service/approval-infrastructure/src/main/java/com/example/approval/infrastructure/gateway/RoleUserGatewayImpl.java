package com.example.approval.infrastructure.gateway;

import com.example.approval.domain.gateway.RoleUserGateway;
import com.example.auth.api.PlanDirectoryApi;
import com.example.auth.api.RoleUserQueryApi;
import com.example.auth.api.dto.ParentPlanResponse;
import com.example.auth.api.dto.PlanMembersResponse;
import com.example.auth.api.dto.RoleUserBatchResponse;
import com.example.auth.api.query.GetParentPlanRequest;
import com.example.auth.api.query.ListPlanMembersRequest;
import com.example.auth.api.query.ListRoleUsersRequest;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色与计划层级用户网关实现（批次 60 接入角色查询、68 接入同计划成员、69 接入父计划成员）。
 * <p>
 * 通过 {@link RoleUserQueryApi} 按角色 + PLAN 精确 scope 批量查询用户，通过
 * {@link PlanDirectoryApi} 查询同计划成员（SAME_PLAN）与直接父计划成员（LEVEL_UP）。
 * auth 不可用、响应异常或数据缺失时一律 fail-closed 返回空，不展开审批人，避免误把
 * 他人任务显示到待办或让节点错误流转。
 *
 * @author approval-service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleUserGatewayImpl implements RoleUserGateway {

  private final RoleUserQueryApi roleUserQueryApi;
  private final PlanDirectoryApi planDirectoryApi;

  @Override
  public Map<String, List<String>> getUsersByRoles(List<String> roleIds, String planNo) {
    if (roleIds == null || roleIds.isEmpty() || planNo == null) {
      return Map.of();
    }

    try {
      ApiResult<RoleUserBatchResponse> result = roleUserQueryApi.listByRoles(
        new ListRoleUsersRequest(roleIds, planNo));
      if (result == null || !result.isSuccess() || result.data() == null
        || result.data().usersByRole() == null) {
        return Map.of();
      }

      Map<String, List<String>> converted = new LinkedHashMap<>();
      result.data().usersByRole().forEach((role, users) ->
        converted.put(role, users == null ? List.of() : users));
      return converted;
    } catch (Exception e) {
      log.warn("角色用户查询失败，fail-closed 不展开角色: planNo={}, roleIds={}",
        planNo, roleIds, e);
      return Map.of();
    }
  }

  @Override
  public List<String> getUsersInSamePlan(String planId, String excludeUser) {
    if (planId == null) {
      return List.of();
    }

    try {
      ApiResult<PlanMembersResponse> result = planDirectoryApi.listMembers(
        new ListPlanMembersRequest(planId, excludeUserNo(excludeUser)));
      return toUserNos(result);
    } catch (Exception e) {
      log.warn("计划成员查询失败，fail-closed 返回空: planNo={}", planId, e);
      return List.of();
    }
  }

  @Override
  public List<String> getUsersInParentPlan(String planNo, String excludeUser) {
    if (planNo == null) {
      return List.of();
    }

    try {
      ApiResult<ParentPlanResponse> parentResult = planDirectoryApi.getParentPlan(
        new GetParentPlanRequest(planNo));
      if (parentResult == null || !parentResult.isSuccess() || parentResult.data() == null
        || parentResult.data().parentPlanNo() == null) {
        return List.of();
      }
      ApiResult<PlanMembersResponse> membersResult = planDirectoryApi.listMembers(
        new ListPlanMembersRequest(parentResult.data().parentPlanNo(), excludeUserNo(excludeUser)));
      return toUserNos(membersResult);
    } catch (Exception e) {
      log.warn("父计划成员查询失败，fail-closed 返回空: planNo={}", planNo, e);
      return List.of();
    }
  }

  private List<String> toUserNos(ApiResult<PlanMembersResponse> result) {
    if (result == null || !result.isSuccess() || result.data() == null
      || result.data().userNos() == null) {
      return List.of();
    }
    return result.data().userNos();
  }

  private String excludeUserNo(String excludeUser) {
    return excludeUser;
  }
}
