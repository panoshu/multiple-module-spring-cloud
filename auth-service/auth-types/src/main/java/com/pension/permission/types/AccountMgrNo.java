package com.pension.permission.types;

import java.util.Objects;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/8/3 10:48
 */
public record AccountMgrNo(
  String value
) {

  public AccountMgrNo {
    Objects.requireNonNull(value, "value");
  }
}
