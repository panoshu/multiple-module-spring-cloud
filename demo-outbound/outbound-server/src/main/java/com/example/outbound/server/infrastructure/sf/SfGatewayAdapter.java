package com.example.outbound.server.infrastructure.sf;

import com.example.outbound.server.domain.logistics.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SfGatewayAdapter
 *
 * @author YourName
 * @since 2025/12/14 21:34
 */
@Component
@RequiredArgsConstructor
public class SfGatewayAdapter implements LogisticsGateway {

  private final SfRetrofitClient sfClient;

  @Override
  public boolean supports(String channel) {
    return "SF".equalsIgnoreCase(channel);
  }

  @Override
  public LogisticsInfo getLogisticsByPhone(String phone) {
    // 1. Convert: Domain -> Infra DTO
    SfQueryRequest request = new SfQueryRequest();
    request.setPhoneCheck(phone);

    // 2. Call: Retrofit (Sync Blocking, handled by Virtual Threads)
    // 建议加上 try-catch 捕获网络异常
    SfQueryResponse response = sfClient.queryRoutes(request);

    // 3. Convert: Infra DTO -> Domain Model
    List<LogisticsNode> nodes = response.getRoutes().stream()
      .map(r -> new LogisticsNode(r.getTime(), r.getRemark()))
      .toList();

    return new LogisticsInfo(phone, LogisticsStatus.EXCEPTION, CargoType.FRESH, nodes);
  }

  @Override
  public LogisticsInfo getLogisticsByTrackingNo(String trackingNo) {
    // 1. Convert: Domain -> Infra DTO
    SfQueryRequest request = new SfQueryRequest();
    request.setTrackingNumber(trackingNo);

    // 2. Call: Retrofit (Sync Blocking, handled by Virtual Threads)
    // 建议加上 try-catch 捕获网络异常
    SfQueryResponse response = sfClient.queryRoutes(request);

    // 3. Convert: Infra DTO -> Domain Model
    List<LogisticsNode> nodes = response.getRoutes().stream()
      .map(r -> new LogisticsNode(r.getTime(), r.getRemark()))
      .toList();

    return new LogisticsInfo(trackingNo, LogisticsStatus.EXCEPTION, CargoType.FRESH, nodes);
  }
}
