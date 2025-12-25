package com.example.outbound.server.infrastructure.yt;

import com.example.outbound.server.domain.logistics.*;
import com.example.outbound.server.exception.OutboundErrorCode;
import com.example.outbound.server.infrastructure.AbstractGateway;
import com.example.shared.core.api.IResultCode;
import com.example.shared.core.api.SystemCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class YtoGatewayAdapter extends AbstractGateway<YtoRequest, YtoResponse> implements LogisticsGateway {

  private final YtoRetrofitClient ytoClient;

  // —————— 1. 策略配置 ——————

  @Override
  public boolean supports(String channel) {
    return "YTO".equalsIgnoreCase(channel);
  }

  @Override
  protected boolean isSuccess(YtoResponse response) {
    return "1".equals(response.getCode());
  }

  @Override
  protected IResultCode getSystemError() {
    return SystemCode.EXTERNAL_SERVICE_ERROR;
  }

  @Override
  protected IResultCode getDefaultBusinessError() {
    return SystemCode.EXTERNAL_SERVICE_ERROR;
  }

  // 【定制点】：圆通有明确的 code 和 msg，我们在配置层定义好提取规则
  // 这样下面的业务方法直接调 executeSimple 就能自动打出漂亮的日志
  @Override
  protected String getDefaultErrorPattern() {
    return "圆通接口业务失败: code={}, msg={}";
  }

  @Override
  protected Object[] extractDefaultErrorArgs(YtoResponse response) {
    return new Object[]{ response.getCode(), response.getMessage() };
  }

  // —————— 2. 业务方法 ——————

  @Override
  public LogisticsInfo getLogisticsByPhone(String phone) {
    return executeSimple(
      "YtoQueryByPhone",
      phone,
      () -> {
        YtoRequest req = new YtoRequest();
        req.setPhoneNo(phone);
        return req;
      },
      ytoClient::queryTrace,
      resp -> toLogisticsInfo(phone, resp)
    );
  }

  @Override
  public LogisticsInfo getLogisticsByTrackingNo(String trackingNo) {
    return executeStandard(
      "YtoQueryByNo",
      trackingNo,
      () -> {
        YtoRequest req = new YtoRequest();
        req.setWaybillNo(trackingNo);
        return req;
      },
      ytoClient::queryTrace,
      resp -> toLogisticsInfo(trackingNo, resp),

      // 1. 业务异常：指定为 "查询失败"
      OutboundErrorCode.LOGISTICS_QUERY_FAILED,
      "圆通业务报错: code={}, msg={}",
      resp -> new Object[]{ resp.getCode(), resp.getMessage() },

      // 2. 系统异常：额外记录运单号，方便运维直接看日志定位
      "圆通接口网络异常, 运单号: {}, 请联系圆通技术支持",
      req -> new Object[]{ req.getWaybillNo() }
    );
  }

  // 私有转换方法
  private LogisticsInfo toLogisticsInfo(String bizId, YtoResponse response) {
    if (response.getData() == null) {
      return new LogisticsInfo(bizId, LogisticsStatus.EXCEPTION, CargoType.FRESH, Collections.emptyList());
    }

    List<LogisticsNode> nodes = response.getData().stream()
      .map(t -> new LogisticsNode(t.getUploadTime(), t.getProcessInfo()))
      .collect(Collectors.toList());

    return new LogisticsInfo(bizId, LogisticsStatus.EXCEPTION, CargoType.FRESH, nodes);
  }
}
