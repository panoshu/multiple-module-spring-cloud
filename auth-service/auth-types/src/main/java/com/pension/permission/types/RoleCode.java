package com.pension.permission.types;

import java.util.Objects;

/**
 * 角色编码，如 AGENT_ENTRY(录入员)、AGENT_REVIEW(审核员)。数据驱动，不写死枚举。
 */
public record RoleCode(String value) {
  public RoleCode {
    Objects.requireNonNull(value, "value");
  }
}
