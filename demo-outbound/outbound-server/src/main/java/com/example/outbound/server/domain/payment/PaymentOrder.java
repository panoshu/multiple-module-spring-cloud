package com.example.outbound.server.domain.payment;

import java.math.BigDecimal;

/**
 * PaymentOrder
 *
 * @author YourName
 * @since 2025/12/14 20:57
 */
public record PaymentOrder(String orderId, BigDecimal amount, String title) {
  public PaymentOrder {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("金额必须大于0");
    }
  }
}
