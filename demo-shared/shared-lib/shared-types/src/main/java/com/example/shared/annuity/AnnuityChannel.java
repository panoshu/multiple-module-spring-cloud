package com.example.shared.annuity;

import com.example.shared.enumeration.CodeEnum;

/**
 * AnnuityChannel
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 20:23
 */
public enum AnnuityChannel implements CodeEnum<String> {
  NETAPP("01", "网上渠道"),
  TELLER("02", "总部柜面"),
  WECHAT("03", "微信"),
  REGIONAL_CENTER("04", "区域中心"),
  BANK_BRANCH("05", "合作银行网点");

  private final String code;
  private final String description;

  AnnuityChannel(String code, String description) {
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
