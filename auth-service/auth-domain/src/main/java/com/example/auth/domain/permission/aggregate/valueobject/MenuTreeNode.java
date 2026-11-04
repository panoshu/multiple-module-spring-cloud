package com.example.auth.domain.permission.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.auth.domain.permission.enumeration.MenuCategory;
import com.example.auth.domain.permission.enumeration.MenuVisibility;
import com.example.auth.types.MenuCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 菜单树节点值对象。
 * <p>组装完毕的树结构节点，供应用层直接序列化为前端响应。
 * 仅包含渲染所需的字段，不暴露聚合根的内部状态。
 */
public record MenuTreeNode(
  MenuCode menuCode,
  String menuName,
  int depth,
  int sortOrder,
  String icon,
  String route,
  MenuCategory menuCategory,
  MenuVisibility visibility,
  boolean enabled,
  List<MenuTreeNode> children
) implements ValueObject {

  public MenuTreeNode {
    if (children == null) {
      children = List.of();
    }
    children = Collections.unmodifiableList(new ArrayList<>(children));
  }

  /**
   * 判断此节点对用户是否可见。
   *
   * @param hasPermission 用户是否拥有本节点绑定的权限（OR 语义）
   * @param hasVisibleChild 本节点是否有任一可见子节点
   * @return true 表示可见
   */
  public boolean isVisible(boolean hasPermission, boolean hasVisibleChild) {
    if (!enabled) {
      return false;
    }
    return switch (visibility) {
      case ALWAYS -> true;
      case WITH_PERMISSION -> hasPermission;
      case WITH_ANY_CHILD -> hasVisibleChild;
    };
  }
}
