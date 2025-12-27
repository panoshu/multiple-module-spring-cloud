package com.example.outbound.server.domain.payment;

import com.example.outbound.dto.payment.PaymentDTO;

/**
 * PaymentGateway
 *
 * @author YourName
 * @since 2025/12/14 20:58
 */
public interface PaymentGateway {
  String executePay(PaymentOrder order);
  PaymentDTO queryMerchantInfo();
}
