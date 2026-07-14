package com.example.integration.infrastructure.core.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * 交易响应头 - 与 TradeReqHead 字段对称
 * 包含完整的交易元数据和状态信息
 */
public record TradeRspHead(
  @JsonProperty("TradeTime") String tradeTime,
  @JsonProperty("TradeDate") String tradeDate,
  @JsonProperty("TradeCode") String tradeCode,
  @JsonProperty("Reserved") String reserved,
  @JsonProperty("ReqSerialNo") String reqSerialNo,
  @JsonProperty("StatusInfo") StatusInfo statusInfo,
  @JsonProperty("TradeSource") TradeSource tradeSource
) implements Serializable {

  public record StatusInfo(
    @JsonProperty("MsgCode") String msgCode,   // 0000-成功，其他-错误码
    @JsonProperty("MsgType") String msgType,   // 1-错误，2-警告等
    @JsonProperty("MsgInfo") String msgInfo    // 详细消息
  ) implements Serializable {
  }
}
