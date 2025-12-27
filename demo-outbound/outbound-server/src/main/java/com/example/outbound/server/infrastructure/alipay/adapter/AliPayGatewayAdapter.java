package com.example.outbound.server.infrastructure.alipay.adapter;

import com.example.outbound.dto.payment.PaymentDTO;
import com.example.outbound.server.domain.payment.PaymentGateway;
import com.example.outbound.server.domain.payment.PaymentOrder;
import com.example.outbound.server.exception.OutboundErrorCode;
import com.example.outbound.server.infrastructure.alipay.AliPayRequest;
import com.example.outbound.server.infrastructure.alipay.AliPayResponse;
import com.example.outbound.server.infrastructure.alipay.AliPayRetrofitClient;
import com.example.shared.core.api.IResultCode;
import com.example.shared.core.api.SystemCode;
import com.example.shared.core.infrastructure.AbstractGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AliPayGatewayAdapter extends AbstractGateway<AliPayRequest, AliPayResponse> implements PaymentGateway {

  private final AliPayRetrofitClient client;

  // —————— 契约实现 ——————
  @Override
  protected boolean isSuccess(AliPayResponse response) {
    return response != null && "10000".equals(response.getCode());
  }

  @Override
  protected IResultCode getSystemError() { return SystemCode.EXTERNAL_SERVICE_ERROR; }

  @Override
  protected IResultCode getDefaultBusinessError() { return OutboundErrorCode.ALIPAY_BIZ_ERROR; } // 建议替换为具体的 ALIPAY_ERROR

  // —————— 业务接口实现 ——————

  @Override
  public String executePay(PaymentOrder order) {
    // [使用说明]: build 模式通过链式调用配置所有细节
    var builder = this.<String>build("AliPayDoPay", order.orderId())
      .request(() -> new AliPayRequest()
        .setOutTradeNo(order.orderId())
        .setAmount(order.amount())
        // 手动注入 Token
        .addHeader("Authorization", getToken())
      )
      .call(req -> client.doPay(req.getHeaders(), req))

      // [高级]: 自定义业务错误码映射
      // 默认情况下，isSuccess=false 会抛出 getDefaultBusinessError()
      // 这里可以根据响应码细分，比如余额不足 vs 账户冻结
      .mapBusinessError(resp -> {
        if ("ACQ.balance_not_enough".equals(resp.getSubCode())) {
          return OutboundErrorCode.BALANCE_NOT_ENOUGH; // 返回特定错误码
        }
        return OutboundErrorCode.PAY_FAILED; // 返回通用错误码
      })

      // [高级]: 自定义日志参数
      // 当发生异常时，默认日志可能看不出具体原因，这里可以提取 subCode/subMsg 打印到日志中
      .onBusinessFailure("支付宝支付失败: subCode={}, subMsg={}",
        resp -> new Object[]{resp.getSubCode(), resp.getSubMsg()})

      .map(AliPayResponse::getTradeNo);

    // 执行调用
    return execute(builder);
  }

  @Override
  public PaymentDTO queryMerchantInfo() {
    // 使用 [BuildVoid] 模式处理无参请求
    var builder = this.<PaymentDTO>buildVoid("AliPayQueryInfo", "Global")
      .call(ignored -> client.queryPaymentInfo())
      .map(resp -> new PaymentDTO("ALIPAY", null, resp.getMerchantStatus()));

    return executeVoid(builder);
  }

  private String getToken() {
    return "mock-token";
  }
}
