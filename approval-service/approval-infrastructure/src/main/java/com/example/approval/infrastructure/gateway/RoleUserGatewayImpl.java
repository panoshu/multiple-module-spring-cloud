package com.example.approval.infrastructure.gateway;

import com.example.approval.domain.gateway.RoleUserGateway;
import com.example.auth.api.PlanDirectoryApi;
import com.example.auth.api.RoleUserQueryApi;
import com.example.auth.api.dto.PlanMembersResponse;
import com.example.auth.api.dto.RoleUserBatchResponse;
import com.example.auth.api.query.ListPlanMembersRequest;
import com.example.auth.api.query.ListRoleUsersRequest;
import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色用户网关实现（批次 60 接入角色查询，批次 68 接入计划成员查询）。
 * <p>
 * 通过 {@link RoleUserQueryApi} 按角色 + PLAN 精确 scope 批量查询用户，通过
 * {@link PlanDirectoryApi} 查询同计划成员（SAME_PLAN 节点）。auth 不可用、响应异常
 * 或数据缺失时一律 fail-closed 返回空，不展开审批人，避免误把他人任务显示到待办
 * 或让节点错误流转。
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
  public Map<String, List<UserNo>> getUsersByRoles(List<String> roleIds, PlanNo planNo) {
    if (roleIds == null || roleIds.isEmpty() || planNo == null) {
      return Map.of();
    }

    try {
      ApiResult<RoleUserBatchResponse> result = roleUserQueryApi.listByRoles(
        new ListRoleUsersRequest(roleIds, planNo.value()));
      if (result == null || !result.isSuccess() || result.data() == null
        || result.data().usersByRole() == null) {
        return Map.of();
      }

      Map<String, List<UserNo>> converted = new LinkedHashMap<>();
      result.data().usersByRole().forEach((role, users) ->
        converted.put(role, users == null ? List.of() : users.stream().map(UserNo::of).toList()));
      return converted;
    } catch (Exception e) {
      log.warn("角色用户查询失败，fail-closed 不展开角色: planNo={}, roleIds={}",
        planNo.value(), roleIds, e);
      return Map.of();
    }
  }

  @Override
  public List<UserNo> getUsersInSamePlan(PlanNo planId, UserNo excludeUser) {
    if (planId == null) {
      return List.of();
    }

    try {
      ApiResult<PlanMembersResponse> result = planDirectoryApi.listMembers(
        new ListPlanMembersRequest(planId.value(),
          excludeUser == null ? null : excludeUser.value()));
      if (result == null || !result.isSuccess() || result.data() == null
        || result.data().userNos() == null) {
        return List.of();
      }
      return result.data().userNos().stream().map(UserNo::of).toList();
    } catch (Exception e) {
      log.warn("计划成员查询失败，fail-closed 返回空: planNo={}", planId.value(), e);
      return List.of();
    }
  }
}
