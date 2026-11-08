package com.example.auth.domain.permission.enumeration;

import com.example.auth.domain.authorization.enumeration.PermissionCategory;

/**
 * 菜单类别。刻意镜像 {@code PermissionCategory}，避免跨子域耦合。
 * <p>桥接映射是平凡的（同名常量），通过 {@link #toPermissionCategory()} 完成。
 */
public enum MenuCategory {
  BUSINESS(true),
  PLATFORM(false);

  private final boolean requiresPlan;

  MenuCategory(boolean requiresPlan) {
    this.requiresPlan = requiresPlan;
  }

  public boolean requiresPlan() {
    return requiresPlan;
  }

  /**
   * 桥接映射到权限类别。同名常量，无需额外映射表。
   */
  public PermissionCategory toPermissionCategory() {
    return PermissionCategory.valueOf(this.name());
  }
}
