package com.example.core.api.material.query;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 校验材料完整性
 *
 * <p>{@code conditionContext} 为可选的业务条件上下文,用于条件必传材料的规则评估。
 *
 * @author panoshu
 */
public record CheckCompletenessQuery(
  @NotBlank(message = "申请单ID不能为空") String applicationId,
  Map<String, Object> conditionContext
) {
}
