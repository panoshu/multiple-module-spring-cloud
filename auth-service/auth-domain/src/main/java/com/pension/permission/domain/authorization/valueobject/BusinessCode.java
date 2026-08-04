package com.pension.permission.domain.authorization.valueobject;

import java.util.Objects;

/**
 * 业务编码，如 缴费、待遇领取、转移。数据驱动，不写死枚举。
 */
public record BusinessCode(String value) {
  public BusinessCode {
    Objects.requireNonNull(value, "value");
  }
}
