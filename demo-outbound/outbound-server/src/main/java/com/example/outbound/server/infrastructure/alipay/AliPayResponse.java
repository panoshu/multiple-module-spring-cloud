package com.example.outbound.server.infrastructure.alipay;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * AliPayResponse
 *
 * @author YourName
 * @since 2025/12/14 21:16
 */
@Data
public class AliPayResponse {

  // 网关返回码，"10000" 代表成功
  @JsonProperty("code")
  private String code;

  @JsonProperty("msg")
  private String msg;

  // 业务返回码
  @JsonProperty("sub_code")
  private String subCode;

  @JsonProperty("sub_msg")
  private String subMsg;

  // 业务数据：支付宝交易号
  @JsonProperty("trade_no")
  private String tradeNo;

  // 业务数据：商户订单号
  @JsonProperty("out_trade_no")
  private String outTradeNo;
}
