package com.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 替换菜单权限绑定命令.
 *
 * @param menuId   菜单ID
 * @param bindings 新的权限绑定列表（全量替换）
 */
public record ReplaceBindingsCommand(
  @NotBlank(message = "menuId不能为空") String menuId,
  @NotNull(message = "bindings不能为null") List<BindingItem> bindings
) {
}
