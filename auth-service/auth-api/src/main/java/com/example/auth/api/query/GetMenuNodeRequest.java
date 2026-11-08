package com.example.auth.api.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询单个菜单节点请求.
 *
 * @param menuId 菜单ID
 */
public record GetMenuNodeRequest(
  @NotBlank(message = "menuId不能为空")
  String menuId
) {
}
