package com.example.approval.api.dto;

import java.util.List;

/**
 * 匹配规则DTO
 *
 * @author approval-service
 */
public record MatchRulesDTO(
    /**
     * 账管人编码列表
     */
    List<String> accountManagerCodes,
    /**
     * 业务类型列表
     */
    List<String> businessTypes,
    /**
     * 金额下限（可选）
     */
    Long amountMin,
    /**
     * 金额上限（可选）
     */
    Long amountMax
) {
}