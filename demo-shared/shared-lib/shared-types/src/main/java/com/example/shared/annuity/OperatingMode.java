package com.example.shared.annuity;

import com.example.shared.enumeration.CodeEnum;

/**
 * 产品的运作模式：本公司在该产品上扮演的角色
 */
public enum OperatingMode implements CodeEnum<String> {
  TRUSTEE_ONLY("01", "单受托"),             // 单受托：本公司为受托人
  ACCOUNT_MGMT_ONLY("02", "单账管"),        // 单账管：本公司为账管人
  TRUSTEE_AND_ACCOUNT_MGMT("03", "受托+账管")  // 受托+账管：本公司同时是受托人和账管人

  ;

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
