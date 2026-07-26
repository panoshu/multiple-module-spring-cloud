package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 业务动作(可扩展枚举)。
 *
 * <p>权限规则通过 Action 集合限定某业务下允许的操作类型,设计文档 3.5 节:
 * <ul>
 *   <li>{@code HANDLE} - 办理业务</li>
 *   <li>{@code QUERY} - 查询业务</li>
 *   <li>{@code AUDIT} - 审核业务</li>
 * </ul>
 * 未来可扩展:EXPORT、IMPORT、APPROVE 等。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum Action implements ValueObject {
  /** 办理 */
  HANDLE,
  /** 查询 */
  QUERY,
  /** 审核 */
  AUDIT
}
