package com.example.outbound.server.infrastructure.yt;

import com.example.outbound.server.domain.logistics.CargoType;
import com.example.outbound.server.domain.logistics.LogisticsGateway;
import com.example.outbound.server.domain.logistics.LogisticsInfo;
import com.example.outbound.server.domain.logistics.LogisticsStatus;
import com.example.outbound.server.exception.OutboundErrorCode;
import com.example.shared.core.api.IResultCode;
import com.example.shared.core.api.SystemCode;
import com.example.shared.core.infrastructure.AbstractGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class YtoGatewayAdapter extends AbstractGateway<YtoRequest, YtoResponse> implements LogisticsGateway {

  private final YtoRetrofitClient client;

  // —————— 契约实现 ——————
  @Override
  protected boolean isSuccess(YtoResponse response) {
    return response != null && "1".equals(response.getCode());
  }

  @Override
  protected IResultCode getSystemError() { return SystemCode.EXTERNAL_SERVICE_ERROR; }

  @Override
  protected IResultCode getDefaultBusinessError() { return OutboundErrorCode.YT_BIZ_ERROR; }

  // —————— 业务接口实现 ——————

  @Override
  public boolean supports(String channel) {
    return "YTO".equalsIgnoreCase(channel);
  }

  @Override
  public LogisticsInfo getLogisticsByTrackingNo(String trackingNo) {
    // [使用说明]: perform 是最简易的调用方式
    // 场景: 当你不需要处理降级，希望上层业务感知到异常时使用。
    return perform(
      "YtoByNo",      // Action Name: 用于日志标记
      trackingNo,     // Biz ID: 用于全链路追踪

      // 1. 请求工厂: 构建请求体，可在此处通过 .addHeader() 注入额外头信息
      () -> new YtoRequest().setWaybillNo(trackingNo).setUserId("GUEST"),

      // 2. 远程调用: 执行 Retrofit 接口
      // [系统异常]: 如果网络超时、DNS失败，ExternalCallTemplate 会捕获并包装为 SystemException
      client::queryTrace,

      // 3. 结果转换: 仅当 isSuccess() 返回 true 时执行
      // [业务异常]: 如果 isSuccess() 返回 false，ExternalCallTemplate 会抛出 BusinessException，此处逻辑不会执行
      this::convertToInfo
    );
    // [异常总结]: 此方法未配置 fallback。
    // - 遇到网络问题 -> 抛出 SystemException (上层 GlobalExceptionHandler 捕获处理)
    // - 遇到圆通报错 -> 抛出 BusinessException (上层捕获处理)
  }

  @Override
  public LogisticsInfo getLogisticsByPhone(String phone) {
    return perform(
      "YtoByPhone",
      phone,
      () -> new YtoRequest().setPhoneNo(phone).setUserId("GUEST"),
      client::queryTrace,
      this::convertToInfo
    );
  }

  private LogisticsInfo convertToInfo(YtoResponse resp) {
    if (resp.getData() != null) {
      resp.getData().stream().map(YtoResponse.TraceInfo::getProcessInfo).toList();
    }
    return new LogisticsInfo("YTO", LogisticsStatus.UNKNOWN, resp.getMessage(), CargoType.UNKNOWN, resp.getNodes());
  }
}
