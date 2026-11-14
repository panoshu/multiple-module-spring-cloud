package com.example.auth.api.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 按角色批量查询用户请求（立项批次 58）。
 * <p>
 * 本立项只支持 {@code PLAN} 精确 scope（按 {@code t_auth_assignment.scope_dimension='PLAN'}
 * 且 {@code scope_value=planNo} 过滤），避免依赖尚未接入的组织目录做
 * CUSTOMER/PRODUCT 继承展开；GLOBAL 与继承维度列为后续。
 * 批量 {@code roleCodes} 用于规避消费侧逐实例展开的 N+1 调用。
 *
 * @param roleCodes 角色编码列表（对应 {@code t_auth_assignment.role_code}）
 * @param planNo    计划号（PLAN 维度的 scope 取值）
 * @author auth-api
 */
public record ListRoleUsersRequest(
  @NotEmpty(message = "角色编码列表不能为空")
  List<String> roleCodes,

  @NotBlank(message = "计划号不能为空")
  String planNo
) {
}
