package com.example.outbound.server.application;

import com.example.outbound.dto.payment.PayCommand;
import com.example.outbound.dto.payment.PayResult;
import com.example.outbound.dto.payment.PaymentDTO;
import com.example.outbound.server.domain.payment.PaymentGateway;
import com.example.outbound.server.domain.payment.PaymentOrder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PaymentApplicationService
 *
 * @author YourName
 * @since 2025/12/14 20:58
 */
@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

  // 注入的是接口，具体实现由 Infra 层提供 (依赖倒置)
  private final PaymentGateway paymentGateway;

  public PaymentDTO getPaymentDTO() {
    return paymentGateway.queryMerchantInfo();
  }

  public PayResult processPayment(@NonNull PayCommand command) {
    // 1. DTO -> Domain Model
    PaymentOrder order = new PaymentOrder(command.orderId(), command.amount(), command.channel());

    // 2. 调用领域服务/网关
    // 这里可以做路由逻辑：比如根据 command.channel() 选择不同的 Gateway 实现
    String txnId = paymentGateway.executePay(order);

    // 3. Domain Result -> DTO
    return new PayResult(txnId, true, "Success");
  }
}
