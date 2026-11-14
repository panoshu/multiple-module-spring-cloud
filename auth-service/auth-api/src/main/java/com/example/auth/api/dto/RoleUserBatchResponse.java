package com.example.auth.api.dto;

import java.util.List;
import java.util.Map;

/**
 * 按角色批量查询用户的响应（立项批次 58）。
 * <p>
 * 只返回用户编号列表，不携带手机号/姓名等敏感字段；未在查询条件中出现的角色
 * 不保证出现在 Map 中，消费侧应按空列表处理。
 *
 * @param usersByRole 角色编码 → 该角色下（指定 PLAN scope 内）ACTIVE 用户编号列表
 * @author auth-api
 */
public record RoleUserBatchResponse(Map<String, List<String>> usersByRole) {
}
