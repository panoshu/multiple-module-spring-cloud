package com.example.outbound.dto.payment;

/**
 * PayResult
 *
 * @author YourName
 * @since 2025/12/14 20:53
 */
public record PayResult(String transactionId, boolean success, String msg) {}
