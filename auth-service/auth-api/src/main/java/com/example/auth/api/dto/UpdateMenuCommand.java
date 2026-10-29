package com.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新菜单显示信息命令.
 *
 * @param menuId    菜单ID
 * @param menuName  菜单名称
 * @param icon      图标标识
 * @param route     前端路由路径
 * @param sortOrder 排序序号
 */
public record UpdateMenuCommand(
  @NotBlank(message = "menuId不能为空") String menuId,
  @NotBlank(message = "menuName不能为空") String menuName,
  String icon,
  String route,
  int sortOrder
) {
}
