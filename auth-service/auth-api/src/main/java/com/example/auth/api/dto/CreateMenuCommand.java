package com.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建菜单命令.
 *
 * @param menuCode     菜单编码（冒号分隔，如 annuity:batch:submit）
 * @param menuName     菜单名称
 * @param parentId     父节点ID（null 表示根节点）
 * @param sortOrder    排序序号
 * @param icon         图标标识
 * @param route        前端路由路径
 * @param menuCategory 菜单类别（BUSINESS / PLATFORM）
 * @param visibility   可见性规则（ALWAYS / WITH_PERMISSION / WITH_ANY_CHILD）
 */
public record CreateMenuCommand(
  @NotBlank(message = "menuCode不能为空") String menuCode,
  @NotBlank(message = "menuName不能为空") String menuName,
  String parentId,
  int sortOrder,
  String icon,
  String route,
  @NotBlank(message = "menuCategory不能为空") String menuCategory,
  @NotBlank(message = "visibility不能为空") String visibility
) {
}
