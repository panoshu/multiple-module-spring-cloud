package com.example.integration.infrastructure.core.common.model;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record TradeReqHead(
  @JsonProperty("TradeTime") String tradeTime,
  @JsonProperty("TradeDate") String tradeDate,
  @JsonProperty("TradeCode") String tradeCode,
  @JsonProperty("Reserved") String reserved,
  @JsonProperty("TradeSource") TradeSource tradeSource
) implements Serializable {

  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

  /**
   * 工厂方法：创建基础 Head
   */
  public static TradeReqHead createDefault(String tradeCode) {
    LocalDateTime now = LocalDateTime.now();
    return new TradeReqHead(
      now.format(TIME_FMT),
      now.format(DATE_FMT),
      tradeCode,
      "7", // 默认保留字段
      TradeSource.createDefault()
    );
  }

  // --- Wither Methods ---

  /**
   * 动态设置柜员信息 (委托给 TradeSource)
   */
  public TradeReqHead withTeller(String tellerNo, String tellerName) {
    return new TradeReqHead(
      this.tradeTime, this.tradeDate, this.tradeCode, this.reserved,
      this.tradeSource.withTeller(tellerNo, tellerName)
    );
  }

  /**
   * 动态设置渠道
   */
  public TradeReqHead withChannel(String channel) {
    return new TradeReqHead(
      this.tradeTime, this.tradeDate, this.tradeCode, this.reserved,
      this.tradeSource.withChannel(channel)
    );
  }
}
