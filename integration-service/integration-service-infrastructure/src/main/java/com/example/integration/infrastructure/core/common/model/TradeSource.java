package com.example.integration.infrastructure.core.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public record TradeSource(
  @JsonProperty("NetworkName") String networkName,
  @JsonProperty("OrganName") String organName,
  @JsonProperty("NetworkNo") String networkNo,
  @JsonProperty("ZoneNo") String zoneNo,
  @JsonProperty("ZoneName") String zoneName,
  @JsonProperty("TradeChannel") String tradeChannel,
  @JsonProperty("TellerName") String tellerName,
  @JsonProperty("TellerNo") String tellerNo,
  @JsonProperty("OrganNo") String organNo
) implements Serializable {

  // --- 默认常量 (根据实际情况调整) ---
  private static final String DEFAULT_ORGAN_NAME = "股份有限公司";
  private static final String DEFAULT_ZONE_NO = "sh";
  private static final String DEFAULT_ZONE_NAME = "总部";
  private static final String DEFAULT_CHANNEL = "7"; // 默认渠道
  private static final String DEFAULT_ORGAN_NO = "yl";
  private static final String DEFAULT_NETWORK_NAME = "yy";
  private static final String DEFAULT_NETWORK_NO = "new";
  private static final String DEFAULT_TELLER = "sys"; // 默认柜员

  /**
   * 工厂方法：创建默认来源
   */
  public static TradeSource createDefault() {
    return new TradeSource(
      DEFAULT_NETWORK_NAME, DEFAULT_ORGAN_NAME, DEFAULT_NETWORK_NO,
      DEFAULT_ZONE_NO, DEFAULT_ZONE_NAME, DEFAULT_CHANNEL,
      DEFAULT_TELLER, DEFAULT_TELLER, DEFAULT_ORGAN_NO
    );
  }

  // --- Wither Methods (返回新对象，实现动态覆盖) ---

  public TradeSource withTeller(String tellerNo, String tellerName) {
    return new TradeSource(
      this.networkName, this.organName, this.networkNo,
      this.zoneNo, this.zoneName, this.tradeChannel,
      tellerName, tellerNo, // 覆盖
      this.organNo
    );
  }

  public TradeSource withChannel(String channel) {
    return new TradeSource(
      this.networkName, this.organName, this.networkNo,
      this.zoneNo, this.zoneName, channel, // 覆盖
      this.tellerName, this.tellerNo, this.organNo
    );
  }
}
