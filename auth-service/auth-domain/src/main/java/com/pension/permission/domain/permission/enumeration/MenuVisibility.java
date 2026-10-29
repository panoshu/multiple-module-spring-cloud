package com.pension.permission.domain.permission.enumeration;

/**
 * 菜单可见性规则。
 * <p>决定菜单节点对用户是否可见的判定策略。
 */
public enum MenuVisibility {
  /** 始终可见（如"首页"、"帮助中心"） */
  ALWAYS,
  /** 用户拥有绑定的权限点时可见 */
  WITH_PERMISSION,
  /** 任一子节点可见时可见（自动向上传播，防止空父节点） */
  WITH_ANY_CHILD
}
