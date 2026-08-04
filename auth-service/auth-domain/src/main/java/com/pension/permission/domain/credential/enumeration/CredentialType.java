package com.pension.permission.domain.credential.enumeration;

import com.example.shared.enumeration.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * CredentialType
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 21:09
 */
@Getter
@AllArgsConstructor
public enum CredentialType implements CodeEnum<String> {
  PASSWORD("01", "密码"),
  U_KEY("02", "UKEY"),
  WECHAT("03", "WECHAT"),

  ;

  private final String code;
  private final String description;
}
