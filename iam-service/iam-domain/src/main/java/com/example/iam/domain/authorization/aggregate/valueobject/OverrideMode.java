package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 覆盖模式 - 高层级权限规则对低层级规则的作用方式。
 *
 * <p>设计文档 3.7 节 PermissionResolver 计算流程步骤 3:
 * <ul>
 *   <li>{@code ADD} - 扩展:向低层级权限集合添加新权限</li>
 *   <li>{@code REMOVE} - 收紧:从低层级权限集合移除权限</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum OverrideMode implements ValueObject {
  /** 扩展:向低层级权限集合添加 */
  ADD,
  /** 收紧:从低层级权限集合移除 */
  REMOVE
}
