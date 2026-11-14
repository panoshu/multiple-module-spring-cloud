package com.example.auth.api.dto;

/**
 * 父计划响应（立项批次 66）。
 * <p>
 * {@code parentPlanNo} 为 {@code null} 表示根计划或组织目录无该计划的层级数据，
 * 消费侧应 fail-closed 回退，不得越级取用户。
 *
 * @param parentPlanNo 直接父计划号（可空）
 * @author auth-api
 */
public record ParentPlanResponse(String parentPlanNo) {
}
