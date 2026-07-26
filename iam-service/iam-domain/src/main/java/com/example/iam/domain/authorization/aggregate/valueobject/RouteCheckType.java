package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 路由校验类型 - 决定 RouteRule 在网关层执行何种校验。
 *
 * <p>设计文档 6.8 节初始化数据:
 * <ul>
 *   <li>{@code LOGIN} - 登录校验(仅校验用户是否登录,不校验权限)</li>
 *   <li>{@code PERMISSION} - 权限校验(校验用户是否拥有指定权限码)</li>
 *   <li>{@code ROLE} - 角色校验(校验用户是否拥有指定角色)</li>
 *   <li>{@code CHANNEL} - 渠道校验(校验请求是否来自指定渠道)</li>
 *   <li>{@code SKIP} - 跳过校验(白名单,不做任何校验)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum RouteCheckType implements ValueObject {
  /** 登录校验 */
  LOGIN,
  /** 权限校验 */
  PERMISSION,
  /** 角色校验 */
  ROLE,
  /** 渠道校验 */
  CHANNEL,
  /** 跳过校验(白名单) */
  SKIP
}
