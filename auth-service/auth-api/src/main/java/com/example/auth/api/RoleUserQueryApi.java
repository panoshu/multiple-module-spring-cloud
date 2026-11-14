package com.example.auth.api;

import com.example.auth.api.dto.RoleUserBatchResponse;
import com.example.auth.api.query.ListRoleUsersRequest;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 用户服务「角色→用户」查询 API（立项批次 58）。
 * <p>
 * 供 approval-service 的 {@code RoleUserGateway} 展开 {@code SPECIFIED_ROLE} 审批节点使用。
 * 服务端按 {@code @RequirePermission} 保护，仅返回用户编号列表。
 *
 * @author auth-api
 */
@HttpExchange("/role-users")
public interface RoleUserQueryApi {

  /**
   * 按角色批量查询指定计划范围内的有效用户。
   *
   * @param request 角色编码列表 + 计划号
   * @return 按角色分组的用户编号
   */
  @PostExchange("/by-roles")
  ApiResult<RoleUserBatchResponse> listByRoles(@RequestBody @Valid ListRoleUsersRequest request);
}
