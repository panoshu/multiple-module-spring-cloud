package com.example.outbound.server.infrastructure.sf;

import com.example.outbound.server.domain.logistics.*;
import com.example.outbound.server.exception.OutboundErrorCode;
import com.example.outbound.server.infrastructure.AbstractGateway;
import com.example.shared.core.api.IResultCode;
import com.example.shared.core.api.SystemCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SfGatewayAdapter extends AbstractGateway<SfQueryRequest, SfQueryResponse> implements LogisticsGateway {

  private final SfRetrofitClient sfClient;

  // —————— 1. 策略配置 ——————

  @Override
  public boolean supports(String channel) {
    return "SF".equalsIgnoreCase(channel);
  }

  @Override
  protected boolean isSuccess(SfQueryResponse response) {
    // 顺丰逻辑：响应不空且路由列表不空即为成功
    return response != null && response.getRoutes() != null;
  }

  @Override
  protected IResultCode getSystemError() {
    return SystemCode.EXTERNAL_SERVICE_ERROR;
  }

  @Override
  protected IResultCode getDefaultBusinessError() {
    // 顺丰一般没有错误码，失败通常意味着查无数据
    return OutboundErrorCode.LOGISTICS_INFO_NOT_FOUND;
  }

  @Override
  protected String getDefaultErrorPattern() {
    return "顺丰接口未返回有效路由信息: {}";
  }

  // —————— 2. 业务方法 ——————

  @Override
  public LogisticsInfo getLogisticsByPhone(String phone) {
    return executeStandard(
      "SfQueryByPhone",
      phone,
      () -> {
        SfQueryRequest req = new SfQueryRequest();
        req.setPhoneCheck(phone);
        return req;
      },
      sfClient::queryRoutes,
      resp -> toLogisticsInfo(phone, resp),

      // 【业务异常配置】
      // 1. 指定错误码
      OutboundErrorCode.LOGISTICS_INFO_NOT_FOUND,
      // 2. 指定错误详情模板 (传递给 BusinessException.withDetail)
      "顺丰查询无数据, 手机号: {}, 顺丰返回: {}",
      // 3. 提取参数
      resp -> new Object[]{ phone, resp }
    );
  }

  @Override
  public LogisticsInfo getLogisticsByTrackingNo(String trackingNo) {
    // 【演示简单场景】
    return executeSimple(
      "SfQueryByNo",
      trackingNo,
      () -> {
        SfQueryRequest req = new SfQueryRequest();
        req.setTrackingNumber(trackingNo);
        return req;
      },
      sfClient::queryRoutes,
      resp -> toLogisticsInfo(trackingNo, resp)
    );
  }

  // 私有转换方法
  private LogisticsInfo toLogisticsInfo(String bizId, SfQueryResponse response) {
    List<LogisticsNode> nodes = response.getRoutes().stream()
      .map(r -> new LogisticsNode(r.getTime(), r.getRemark()))
      .toList();
    return new LogisticsInfo(bizId, LogisticsStatus.EXCEPTION, CargoType.FRESH, nodes);
  }
}
