package com.pension.permission.domain.permission.service;

import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.permission.aggregate.PermissionMenu;
import com.pension.permission.domain.permission.aggregate.valueobject.MenuTreeNode;
import com.pension.permission.domain.permission.enumeration.MenuCategory;
import com.pension.permission.domain.permission.enumeration.MenuVisibility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单树组装领域服务。
 * <p>将扁平的 {@link PermissionMenu} 列表组装为树形结构，
 * 并根据用户的权限集合过滤出可见节点。
 * <p>纯函数，无副作用，不依赖 Spring Bean。
 */
public final class MenuTreeAssembler {

  private MenuTreeAssembler() {}

  /**
   * 将扁平菜单列表组装为可见的树形结构。
   *
   * @param flatMenus 全部菜单节点（扁平）
   * @param userPermissions 用户拥有的权限集合
   * @param categoryBridge 将 {@link MenuCategory} 映射为 {@link PermissionCategory} 的桥接函数
   * @return 过滤后的树根列表
   */
  public static List<MenuTreeNode> assembleVisibleTree(
    List<PermissionMenu> flatMenus,
    Set<Permission> userPermissions,
    java.util.function.Function<MenuCategory, PermissionCategory> categoryBridge
  ) {
    // 1. 按 parentId 分组，保持插入顺序
    Map<String, List<PermissionMenu>> childrenMap = new LinkedHashMap<>();
    List<PermissionMenu> roots = new ArrayList<>();

    for (PermissionMenu menu : flatMenus) {
      if (!menu.enabled()) {
        continue;
      }
      String parentKey = menu.parentId() != null ? menu.parentId().value() : null;
      if (parentKey == null) {
        roots.add(menu);
      } else {
        childrenMap.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(menu);
      }
    }

    // 2. 排序
    Comparator<PermissionMenu> bySort = Comparator.comparingInt(PermissionMenu::sortOrder);
    roots.sort(bySort);
    childrenMap.values().forEach(list -> list.sort(bySort));

    // 3. 递归构建树并过滤可见节点
    List<MenuTreeNode> result = new ArrayList<>();
    for (PermissionMenu root : roots) {
      MenuTreeNode node = buildVisibleNode(root, childrenMap, userPermissions, categoryBridge);
      if (node != null) {
        result.add(node);
      }
    }
    return result;
  }

  /**
   * 将扁平菜单列表组装为完整树形结构（不过滤，管理端使用）。
   */
  public static List<MenuTreeNode> assembleFullTree(List<PermissionMenu> flatMenus) {
    Map<String, List<PermissionMenu>> childrenMap = new LinkedHashMap<>();
    List<PermissionMenu> roots = new ArrayList<>();

    for (PermissionMenu menu : flatMenus) {
      String parentKey = menu.parentId() != null ? menu.parentId().value() : null;
      if (parentKey == null) {
        roots.add(menu);
      } else {
        childrenMap.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(menu);
      }
    }

    Comparator<PermissionMenu> bySort = Comparator.comparingInt(PermissionMenu::sortOrder);
    roots.sort(bySort);
    childrenMap.values().forEach(list -> list.sort(bySort));

    return roots.stream()
      .map(root -> buildFullNode(root, childrenMap))
      .toList();
  }

  private static MenuTreeNode buildVisibleNode(
    PermissionMenu menu,
    Map<String, List<PermissionMenu>> childrenMap,
    Set<Permission> userPermissions,
    java.util.function.Function<MenuCategory, PermissionCategory> categoryBridge
  ) {
    // 先递归构建子树
    List<MenuTreeNode> visibleChildren = new ArrayList<>();
    List<PermissionMenu> childMenus = childrenMap.getOrDefault(menu.id().value(), List.of());
    for (PermissionMenu child : childMenus) {
      MenuTreeNode childNode = buildVisibleNode(child, childrenMap, userPermissions, categoryBridge);
      if (childNode != null) {
        visibleChildren.add(childNode);
      }
    }

    // 判定本节点的可见性
    boolean hasPermission = checkPermissionBinding(menu, userPermissions, categoryBridge);
    boolean hasVisibleChild = !visibleChildren.isEmpty();

    MenuTreeNode tempNode = new MenuTreeNode(
      menu.menuCode(), menu.menuName(), menu.depth(), menu.sortOrder(),
      menu.icon(), menu.route(), menu.menuCategory(), menu.visibility(),
      menu.enabled(), visibleChildren);

    if (!tempNode.isVisible(hasPermission, hasVisibleChild)) {
      return null;
    }

    return tempNode;
  }

  private static boolean checkPermissionBinding(
    PermissionMenu menu,
    Set<Permission> userPermissions,
    java.util.function.Function<MenuCategory, PermissionCategory> categoryBridge
  ) {
    if (menu.bindings().isEmpty()) {
      return false;
    }
    PermissionCategory permCategory = categoryBridge.apply(menu.menuCategory());
    return menu.bindings().stream().anyMatch(binding -> {
      Permission perm = new Permission(binding.businessCode(), binding.actionCode());
      return userPermissions.contains(perm)
        || userPermissions.stream().anyMatch(up -> up.covers(perm.businessCode(), perm.actionCode()));
    });
  }

  private static MenuTreeNode buildFullNode(
    PermissionMenu menu,
    Map<String, List<PermissionMenu>> childrenMap
  ) {
    List<PermissionMenu> childMenus = childrenMap.getOrDefault(menu.id().value(), List.of());
    List<MenuTreeNode> childNodes = childMenus.stream()
      .map(child -> buildFullNode(child, childrenMap))
      .toList();

    return new MenuTreeNode(
      menu.menuCode(), menu.menuName(), menu.depth(), menu.sortOrder(),
      menu.icon(), menu.route(), menu.menuCategory(), menu.visibility(),
      menu.enabled(), childNodes);
  }
}
