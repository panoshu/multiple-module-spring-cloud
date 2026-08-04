package com.pension.permission.domain.channel.enumeration;

import com.example.shared.enumeration.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * SessionStatus
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/4 19:40
 */
@Getter
@RequiredArgsConstructor
public enum SessionStatus implements CodeEnum<String> {
  ACTIVE("active", "活跃"),

  CLOSED("closed", "已关闭"),

  EXPIRED("expired", "已过期")
  ;

  private final String code;
  private final String description;
}
