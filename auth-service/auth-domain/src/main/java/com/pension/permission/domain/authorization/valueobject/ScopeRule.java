package com.pension.permission.domain.authorization.valueobject;


import com.pension.permission.domain.authorization.enumeration.ScopeDimension;

import java.util.Objects;

/**
 * 一条范围条件。一个Grant下的多条ScopeRule是"且"的关系(见ScopeMatcher)，
 * 多个Grant之间则是"或"的关系(见EffectResolver)——这跟IAM策略语句的组合方式一致。
 * inheritable 只在 dimension=CUSTOMER 时有意义，表示是否级联到该客户的下级客户。
 */
public record ScopeRule(ScopeDimension dimension, String value, boolean inheritable) {

  public ScopeRule {
    Objects.requireNonNull(dimension, "dimension");
    Objects.requireNonNull(value, "value");
  }

  public static ScopeRule of(ScopeDimension dimension, String value) {
    return new ScopeRule(dimension, value, false);
  }
}
