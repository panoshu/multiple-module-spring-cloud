package com.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 移动菜单节点命令.
 *
 * @param menuId       菜单ID
 * @param newParentId  新父节点ID（null 表示移到根）
 * @param sortOrder    新排序序号
 */
public record MoveMenuCommand(
  @NotBlank(message = "menuId不能为空") String menuId,
  String newParentId,
  int sortOrder
) {
}
