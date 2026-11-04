package com.example.core.types;

import com.example.shared.enumeration.CodeEnum;

/**
 * 产品的运作模式：本公司在该产品上扮演的角色
 */
public enum OperatingMode implements CodeEnum<String> {
  TRUSTEE_ONLY("01", "单受托"),
  ACCOUNT_MGMT_ONLY("02", "单账管"),
  TRUSTEE_AND_ACCOUNT_MGMT("03", "受托+账管");

  private final String code;
  private final String description;

  OperatingMode(String code, String description) {
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
