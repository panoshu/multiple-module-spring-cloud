package com.example.auth.adapter.converter;

import com.example.auth.api.dto.*;
import com.example.auth.domain.authorization.valueobject.ActionCode;
import com.example.auth.domain.authorization.valueobject.BusinessCode;
import com.example.auth.domain.permission.aggregate.PermissionMenu;
import com.example.auth.domain.permission.aggregate.valueobject.MenuTreeNode;
import com.example.auth.domain.permission.aggregate.valueobject.PermissionBinding;
import com.example.auth.types.MenuCode;
import com.example.auth.types.PermissionBindingId;
import com.example.auth.types.PermissionMenuId;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 权限菜单 Adapter 层转换器.
 *
 * @author auth-adapter
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class PermissionMenuConverter {

  // ── 前端可见树响应 ──

  public MenuTreeNodeResponse toTreeNodeResponse(MenuTreeNode node) {
    if (node == null) {
      return null;
    }
    return new MenuTreeNodeResponse(
      node.menuCode().value(),
      node.menuName(),
      node.depth(),
      node.sortOrder(),
      node.icon(),
      node.route(),
      node.menuCategory().name(),
      node.visibility().name(),
      node.enabled(),
      toTreeNodeResponseList(node.children())
    );
  }

  public List<MenuTreeNodeResponse> toTreeNodeResponseList(List<MenuTreeNode> nodes) {
    if (nodes == null) {
      return List.of();
    }
    return nodes.stream().map(this::toTreeNodeResponse).toList();
  }

  // ── 管理端扁平响应 ──

  public MenuNodeResponse toNodeResponse(PermissionMenu menu) {
    if (menu == null) {
      return null;
    }
    return new MenuNodeResponse(
      menu.id().value(),
      menu.menuCode().value(),
      menu.menuName(),
      menu.menuPath(),
      menu.parentId() != null ? menu.parentId().value() : null,
      menu.depth(),
      menu.sortOrder(),
      menu.icon(),
      menu.route(),
      menu.menuCategory().name(),
      menu.visibility().name(),
      menu.enabled(),
      toBindingResponseList(menu.bindings())
    );
  }

  public List<MenuNodeResponse> toNodeResponseList(List<PermissionMenu> menus) {
    if (menus == null) {
      return List.of();
    }
    return menus.stream().map(this::toNodeResponse).toList();
  }

  // ── 权限绑定响应 ──

  public PermissionBindingResponse toBindingResponse(PermissionBinding binding) {
    if (binding == null) {
      return null;
    }
    return new PermissionBindingResponse(
      binding.bindingId().value(),
      binding.businessCode().value(),
      binding.actionCode() != null ? binding.actionCode().value() : null
    );
  }

  public List<PermissionBindingResponse> toBindingResponseList(List<PermissionBinding> bindings) {
    if (bindings == null) {
      return List.of();
    }
    return bindings.stream().map(this::toBindingResponse).toList();
  }

  // ── 命令 → 领域对象 ──

  public PermissionMenuId toMenuId(String menuId) {
    return menuId != null ? new PermissionMenuId(menuId) : null;
  }

  public MenuCode toMenuCode(String menuCode) {
    return menuCode != null ? new MenuCode(menuCode) : null;
  }

  public List<PermissionBinding> toBindingList(List<BindingItem> items) {
    if (items == null) {
      return List.of();
    }
    return items.stream().map(this::toBinding).toList();
  }

  public PermissionBinding toBinding(BindingItem item) {
    if (item == null) {
      return null;
    }
    return new PermissionBinding(
      new PermissionBindingId(java.util.UUID.randomUUID().toString()),
      new BusinessCode(item.businessCode()),
      item.actionCode() != null && !item.actionCode().isEmpty()
        ? new ActionCode(item.actionCode()) : null
    );
  }
}
