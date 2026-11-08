package com.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 切换菜单启停命令.
 *
 * @param menuId 菜单ID
 */
public record ToggleMenuCommand(
  @NotBlank(message = "menuId不能为空") String menuId
) {
}
