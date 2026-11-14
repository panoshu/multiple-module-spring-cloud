package com.example.auth.api.dto;

import java.util.List;

/**
 * 计划成员响应（立项批次 66）。
 * <p>
 * 只返回用户编号列表，不携带姓名 / 手机号等敏感字段；无匹配用户或组织目录
 * 不可用时返回空列表（fail-closed）。
 *
 * @param userNos 计划范围内有效用户编号列表
 * @author auth-api
 */
public record PlanMembersResponse(List<String> userNos) {
}
