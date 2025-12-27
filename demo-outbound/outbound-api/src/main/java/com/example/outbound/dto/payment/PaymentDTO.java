package com.example.outbound.dto.payment;

/**
 * PaymentDTO
 *
 * @author YourName
 * @since 2025/12/14 20:30
 */
public record PaymentDTO(
  String channel,
  String tradeNo,
  String status
) {
}
