package com.pension.permission.domain.user.enumeration;

import com.example.shared.enumeration.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * UserStatus
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 15:36
 */

@Getter
@AllArgsConstructor
public enum UserStatus implements CodeEnum<String> {

  ACTIVE("00", "激活"),
  FROZEN("01", "冻结"),
  DISABLED("02", "注销");

  private final String code;
  private final String description;
}
