package com.pension.permission.domain.credential.enumeration;

import com.example.shared.enumeration.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * CredentialStatus
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 19:54
 */
@Getter
@AllArgsConstructor
public enum CredentialStatus implements CodeEnum<String> {
  ACTIVE("01", "激活"),
  REVOKED("02", "撤销"),
  DISABLED("03", "失效");

  private final String code;
  private final String description;
}
