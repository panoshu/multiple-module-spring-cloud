package com.example.outbound.dto.payment;

import java.math.BigDecimal;

/**
 * PayCommand
 *
 * @author YourName
 * @since 2025/12/14 20:52
 */
public record PayCommand(String orderId, BigDecimal amount, String channel) {}
