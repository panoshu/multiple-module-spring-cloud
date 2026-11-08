package com.example.auth.api.dto;

import java.util.List;

/**
 * 菜单树节点响应（前端可见树）.
 *
 * @param menuCode     菜单编码（稳定标识，如 annuity:batch:submit）
 * @param menuName     菜单名称
 * @param depth        树深度
 * @param sortOrder    排序序号
 * @param icon         图标标识
 * @param route        前端路由路径
 * @param menuCategory 菜单类别（BUSINESS / PLATFORM）
 * @param visibility   可见性规则（ALWAYS / WITH_PERMISSION / WITH_ANY_CHILD）
 * @param enabled      是否启用
 * @param children     子节点
 */
public record MenuTreeNodeResponse(
  String menuCode,
  String menuName,
  int depth,
  int sortOrder,
  String icon,
  String route,
  String menuCategory,
  String visibility,
  boolean enabled,
  List<MenuTreeNodeResponse> children
) {
}
