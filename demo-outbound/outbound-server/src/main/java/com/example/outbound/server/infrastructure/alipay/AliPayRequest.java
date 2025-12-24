package com.example.outbound.server.infrastructure.alipay;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * AliPayRequest
 *
 * @author YourName
 * @since 2025/12/14 21:16
 */
@Data
@Accessors(chain = true)
public class AliPayRequest {

  // 映射 JSON 中的 "out_trade_no"
  @JsonProperty("out_trade_no")
  private String outTradeNo;

  @JsonProperty("total_amount")
  private String totalAmount;

  @JsonProperty("subject")
  private String subject;

  @JsonProperty("product_code")
  private String productCode = "FAST_INSTANT_TRADE_PAY";

}
