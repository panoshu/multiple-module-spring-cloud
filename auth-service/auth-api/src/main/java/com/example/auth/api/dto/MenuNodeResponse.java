package com.example.auth.api.dto;

import java.util.List;

/**
 * 菜单节点管理端响应（扁平，含绑定信息）.
 *
 * @param menuId       菜单ID
 * @param menuCode     菜单编码
 * @param menuName     菜单名称
 * @param menuPath     物化路径
 * @param parentId     父节点ID
 * @param depth        树深度
 * @param sortOrder    排序序号
 * @param icon         图标标识
 * @param route        前端路由路径
 * @param menuCategory 菜单类别
 * @param visibility   可见性规则
 * @param enabled      是否启用
 * @param bindings     权限绑定列表
 */
public record MenuNodeResponse(
  String menuId,
  String menuCode,
  String menuName,
  String menuPath,
  String parentId,
  int depth,
  int sortOrder,
  String icon,
  String route,
  String menuCategory,
  String visibility,
  boolean enabled,
  List<PermissionBindingResponse> bindings
) {
}
