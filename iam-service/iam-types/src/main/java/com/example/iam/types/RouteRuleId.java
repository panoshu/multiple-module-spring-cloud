package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;
import java.util.Objects;

/**
 * 路由规则 ID(网关路由规则的标识,用于 demo-gateway 渠道识别与登录校验)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record RouteRuleId(Long value) implements Identifier<Long> {

  public RouteRuleId {
    Objects.requireNonNull(value, "RouteRuleId value cannot be null");
  }

  public static RouteRuleId of(Long value) {
    return new RouteRuleId(value);
  }

  public static RouteRuleId of(String value) {
    return new RouteRuleId(Long.parseLong(value));
  }

  public long longValue() {
    return value;
  }
}
