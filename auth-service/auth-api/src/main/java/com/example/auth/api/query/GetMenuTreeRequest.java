package com.example.auth.api.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 获取菜单树请求.
 *
 * @param planId 计划ID（业务菜单必传）
 */
public record GetMenuTreeRequest(
  @NotBlank(message = "planId不能为空")
  String planId
) {
}
