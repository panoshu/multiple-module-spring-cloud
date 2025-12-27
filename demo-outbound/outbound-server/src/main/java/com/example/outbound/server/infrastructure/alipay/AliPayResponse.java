package com.example.outbound.server.infrastructure.alipay;

import com.example.shared.core.model.BaseExternalResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AliPayResponse
 *
 * @author YourName
 * @since 2025/12/14 21:16
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AliPayResponse extends BaseExternalResponse {
  private String code;
  private String msg;
  private String tradeNo;
  private String merchantStatus;
  private String subCode;
  private String subMsg;
}
