package com.example.outbound.server.infrastructure.alipay;

import com.example.outbound.dto.payment.PaymentDTO;
import com.example.outbound.server.domain.payment.PaymentGateway;
import com.example.outbound.server.domain.payment.PaymentOrder;
import com.example.outbound.server.exception.OutboundErrorCode;
import com.example.shared.core.exception.BusinessException;
import com.example.shared.core.exception.SystemException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AliPayGatewayAdapter
 *
 * @author YourName
 * @since 2025/12/14 21:04
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AliPayGatewayAdapter implements PaymentGateway {

  private final AliPayRetrofitClient aliPayClient;

  @Override
  public String executePay(PaymentOrder order) {
    // Step 1: 转换 (Domain -> External DTO)
    AliPayRequest request = new AliPayRequest();
    request.setOutTradeNo(order.bizId());
    request.setTotalAmount(order.amount().toString());

    // Step 2: 调用 (Retrofit 同步阻塞，交由虚拟线程调度)
    try {

      AliPayResponse response = aliPayClient.doPay(request);

      // Step 3: 校验与转换 (External DTO -> Domain Return)
      if (!"10000".equals(response.getCode())) {
        throw new BusinessException(
          OutboundErrorCode.EXTERNAL_SERVICE_ERROR,
          "Alipay Failed: " + response.getSubMsg()
        );
      }

      return response.getTradeNo();

    } catch (BusinessException e) {
      // 如果已经是封装好的异常，直接抛出
      throw e;
    } catch (Exception e) {
      // 3. 处理网络层面的失败 (超时、DNS解析失败、连接被重置)
      log.error("Call Alipay error", e);
      throw SystemException.wrap(e);
    }
  }

  @Override
  public PaymentDTO getPaymentDTO() {
    return new PaymentDTO("name", 1, "type");
  }
}
