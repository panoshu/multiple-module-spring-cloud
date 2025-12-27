package com.example.outbound.server.infrastructure.sf;

import com.example.outbound.server.domain.logistics.*;
import com.example.outbound.server.exception.OutboundErrorCode;
import com.example.shared.core.api.IResultCode;
import com.example.shared.core.api.SystemCode;
import com.example.shared.core.infrastructure.AbstractGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SfGatewayAdapter extends AbstractGateway<SfQueryRequest, SfQueryResponse> implements LogisticsGateway {

  private final SfRetrofitClient client;

  // —————— 契约实现 ——————
  @Override
  protected boolean isSuccess(SfQueryResponse response) {
    return response != null && "OK".equals(response.getHead());
  }

  @Override
  protected IResultCode getSystemError() { return SystemCode.EXTERNAL_SERVICE_ERROR; }

  @Override
  protected IResultCode getDefaultBusinessError() { return OutboundErrorCode.SF_BIZ_ERROR; }

  // —————— 业务接口实现 ——————

  @Override
  public boolean supports(String channel) {
    return "SF".equalsIgnoreCase(channel);
  }

  @Override
  public LogisticsInfo getLogisticsByTrackingNo(String trackingNo) {
    return perform(
      "SfByNo",
      trackingNo,
      () -> new SfQueryRequest().setTrackingNumber(trackingNo),
      req -> client.doQuery(req.getHeaders(), req),

      // 成功时的转换逻辑
      this::convertToInfo,

      // [使用说明]: fallback 函数 (降级逻辑)
      // 场景: 顺丰接口挂了，或者查不到数据，不想让前端报错，而是显示"暂无轨迹"。
      // [触发时机]:
      // 1. 系统异常 (网络断了、超时)
      // 2. 业务异常 (isSuccess 返回 false, 比如顺丰返回 Head:ERR)
      // 3. 转换异常 (convertToInfo 抛错)
      // [参数 ex]: 原始异常对象，可用于记录日志
      ex -> {
        log.warn("顺丰接口调用降级, 单号: {}, 原因: {}", trackingNo, ex.getMessage());
        // 返回一个"空"对象，保证业务不中断
        return new LogisticsInfo("SF", LogisticsStatus.UNKNOWN, "暂无数据(降级)", CargoType.UNKNOWN, Collections.emptyList());
      }
    );
  }

  @Override
  public LogisticsInfo getLogisticsByPhone(String phone) {
    return queryLogistics("SfByPhone", phone,
      () -> new SfQueryRequest().setCheckPhoneNo(phone)); // 顺丰可能有专门的手机号查询字段
  }

  // 私有复用方法：演示 [Perform] 带降级
  private LogisticsInfo queryLogistics(String action, String bizId, java.util.function.Supplier<SfQueryRequest> reqFactory) {
    return perform(
      action,
      bizId,
      reqFactory,
      // Remote Call
      req -> client.doQuery(req.getHeaders(), req),
      // Success Mapping
      resp -> {
        List<LogisticsNode> nodes = resp.getNodes() == null
          ? Collections.emptyList()
          : resp.getNodes();
        return new LogisticsInfo("SF", resp.getStatus(), "查询成功", resp.getType(), nodes);
      },
      // Fallback (降级)
      ex -> {
        log.warn("顺丰查询降级: {}", ex.getMessage());
        return new LogisticsInfo("SF", LogisticsStatus.UNKNOWN, "暂无数据(降级)", CargoType.UNKNOWN, Collections.emptyList());
      }
    );
  }

  private LogisticsInfo convertToInfo(SfQueryResponse resp) {
    if (resp.getData() != null) {
      resp.getData().stream().map(SfQueryResponse.TraceInfo::getProcessInfo).toList();
    }
    return new LogisticsInfo("YTO", LogisticsStatus.UNKNOWN, resp.getMessage(), CargoType.UNKNOWN, resp.getNodes());
  }
}
