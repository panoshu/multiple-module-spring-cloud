package com.example.outbound.server.interfaces;

import com.example.outbound.api.GatewayPaymentApi;
import com.example.outbound.dto.payment.PayCommand;
import com.example.outbound.dto.payment.PayResult;
import com.example.outbound.dto.payment.PaymentDTO;
import com.example.outbound.server.application.PaymentApplicationService;
import com.example.shared.core.api.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * PaymentController
 *
 * @author YourName
 * @since 2025/12/14 21:09
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentController implements GatewayPaymentApi {

  private final PaymentApplicationService applicationService;

  @Override
  public Result<PaymentDTO> getPayment(int paymentId) {
    log.info(">>> [Controller] Handling Request | Thread: {} | IsVirtual: {}",
      Thread.currentThread(), Thread.currentThread().isVirtual());
    return Result.success(applicationService.getPaymentDTO());
  }

  @Override
  public Result<PayResult> pay(PayCommand command) {
    return Result.success(applicationService.processPayment(command));
  }
}
