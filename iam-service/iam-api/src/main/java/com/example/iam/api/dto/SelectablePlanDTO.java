package com.example.iam.api.dto;

/**
 * 可选计划DTO
 *
 * <p>用于计划选择列表展示,声明当前用户可操作的计划信息(含代办来源)。
 *
 * @author iam-service
 */
public record SelectablePlanDTO(
    /**
     * 计划编号
     */
    String planId,
    /**
     * 计划名称
     */
    String planName,
    /**
     * 客户编号
     */
    String customerNo,
    /**
     * 客户名称
     */
    String customerName,
    /**
     * 运营模式
     */
    String operationMode,
    /**
     * 是否为代办计划(通过 PlanDelegation 获得)
     */
    boolean isDelegated,
    /**
     * 代办来源计划编号(非代办计划时为空)
     */
    String delegatorPlanNo
) {
}
