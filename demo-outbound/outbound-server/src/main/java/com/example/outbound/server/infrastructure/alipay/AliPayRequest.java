package com.example.outbound.server.infrastructure.alipay;

import com.example.shared.core.model.BaseExternalRequest;
import com.example.shared.core.trace.context.BizContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * AliPayRequest
 *
 * @author YourName
 * @since 2025/12/14 21:16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class AliPayRequest extends BaseExternalRequest<AliPayRequest> { // 泛型传入自己
  private String outTradeNo;
  private BigDecimal amount;
  private String subject;

  // 构造时可以自动放入一些通用 Header
  public AliPayRequest() {
    // 例如：自动透传当前的 BatchId
    this.addHeader("X-Batch-Id", BizContext.getBatchId());
  }
}
