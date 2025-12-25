package com.example.outbound.server.infrastructure.alipay;

import com.example.outbound.dto.payment.PaymentDTO;
import com.example.outbound.server.domain.payment.PaymentGateway;
import com.example.outbound.server.domain.payment.PaymentOrder;
import com.example.outbound.server.exception.OutboundErrorCode;
import com.example.outbound.server.infrastructure.AbstractGateway;
import com.example.shared.core.api.IResultCode;
import com.example.shared.core.api.SystemCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AliPayGatewayAdapter extends AbstractGateway<AliPayRequest, AliPayResponse> implements PaymentGateway {

  private final AliPayRetrofitClient client;

  // —————— 1. 策略配置 ——————

  @Override
  protected boolean isSuccess(AliPayResponse response) {
    return "10000".equals(response.getCode());
  }

  @Override
  protected IResultCode getSystemError() {
    return SystemCode.EXTERNAL_SERVICE_ERROR;
  }

  @Override
  protected IResultCode getDefaultBusinessError() {
    return OutboundErrorCode.ALIPAY_BIZ_ERROR;
  }

  // 覆盖默认日志格式，增加 SubMsg
  @Override
  protected String getDefaultErrorPattern() {
    return "支付宝接口异常: code={}, msg={}, subMsg={}";
  }

  @Override
  protected Object[] extractDefaultErrorArgs(AliPayResponse response) {
    return new Object[]{ response.getCode(), response.getMsg(), response.getSubMsg() };
  }

  // —————— 2. 业务方法 ——————

  @Override
  public String executePay(PaymentOrder order) {
    var builder = this.<String>buildCall("ExecuteAliPay", order.bizId())
      .request(() -> new AliPayRequest().setOutTradeNo(order.bizId()))
      .call(client::doPay)

      // 【自定义系统异常信息】
      // 如果网络挂了，BaseException.detailMessage 会包含这个信息
      .onSystemFailure(
        "支付宝连接失败, 订单号: {}, 请检查网络配置或证书",
        req -> new Object[]{ req.getOutTradeNo() }
      )

      // 【自定义业务异常信息】
      // 动态映射错误码 + 动态提取错误参数
      .mapBusinessError(resp -> {
        if ("40004".equals(resp.getCode())) return OutboundErrorCode.USER_NOT_EXIST;
        return OutboundErrorCode.PAY_FAILED;
      })
      .onBusinessFailure(
        "支付拒绝: subCode={}, subMsg={}, 建议重试",
        resp -> new Object[]{ resp.getSubCode(), resp.getSubMsg() }
      )

      .map(AliPayResponse::getTradeNo);

    return execute(builder);
  }

  @Override
  public PaymentDTO getPaymentDTO() {
    // 模拟实现
    return new PaymentDTO("支付宝", 1, "ONLINE");
  }
}
