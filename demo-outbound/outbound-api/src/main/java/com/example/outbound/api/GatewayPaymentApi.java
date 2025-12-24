package com.example.outbound.api;

import com.example.outbound.dto.payment.PayCommand;
import com.example.outbound.dto.payment.PayResult;
import com.example.outbound.dto.payment.PaymentDTO;
import com.example.shared.core.api.Result;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * GatewayPaymentClient
 *
 * @author YourName
 * @since 2025/12/14 20:35
 */

@HttpExchange("outbound/payment")
public interface GatewayPaymentApi {
  @GetExchange("/{id}")
  Result<PaymentDTO> getPayment(@PathVariable("id") int paymentId);

  @PostExchange("/pay")
  Result<PayResult> pay(@RequestBody PayCommand command);
}
