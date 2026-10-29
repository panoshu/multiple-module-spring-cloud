package com.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 删除菜单命令.
 *
 * @param menuId 菜单ID
 */
public record DeleteMenuCommand(
  @NotBlank(message = "menuId不能为空") String menuId
) {
}
