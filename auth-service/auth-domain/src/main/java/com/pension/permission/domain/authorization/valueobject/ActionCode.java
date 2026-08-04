package com.pension.permission.domain.authorization.valueobject;

import java.util.Objects;

/**
 * 操作编码，如 办理、查询、审核。不同业务可以有不同的操作集合，数据驱动。
 */
public record ActionCode(String value) {
  public ActionCode {
    Objects.requireNonNull(value, "value");
  }
}
