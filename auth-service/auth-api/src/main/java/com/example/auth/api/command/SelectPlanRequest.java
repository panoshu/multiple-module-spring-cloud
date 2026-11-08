package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 选择计划请求.
 *
 * @param planId 计划编号
 */
public record SelectPlanRequest(
  @NotBlank String planId
) {
}
