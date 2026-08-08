package com.example.shared.identity;

import com.example.shared.enumeration.CodeEnum;

/**
 * IdentityType
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 17:35
 */

public enum IdentityType implements CodeEnum<String> {

  ID_CARD("01", "居民身份证"),
  PASSPORT("02", "护照"),
  HK_MACAO_PERMIT("03", "港澳居民通行证"),
  TAIWAN_PERMIT("04", "台湾居民通行证");

  private final String code;
  private final String description;

  IdentityType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getDescription() {
    return description;
  }
}
