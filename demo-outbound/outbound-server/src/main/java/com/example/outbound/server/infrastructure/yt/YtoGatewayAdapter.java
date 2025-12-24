package com.example.outbound.server.infrastructure.yt;

import com.example.outbound.server.domain.logistics.*;
import com.example.outbound.server.exception.OutboundErrorCode;
import com.example.shared.core.exception.BusinessException;
import com.example.shared.core.exception.SystemException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * YtoGatewayAdapter
 *
 * @author YourName
 * @since 2025/12/14 21:49
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YtoGatewayAdapter implements LogisticsGateway {

  private final YtoRetrofitClient ytoClient;

  @Override
  public boolean supports(String channel) {
    // 策略模式：告诉调用者，我支持 "YTO" 渠道
    return "YTO".equalsIgnoreCase(channel);
  }

  @Override
  public LogisticsInfo getLogisticsByPhone(String phone) {
    // 1. Convert: Domain -> YTO DTO
    YtoRequest request = new YtoRequest();
    request.setPhoneNo(phone);

    // 2. Call: Retrofit
    YtoResponse response;
    try {
      response = ytoClient.queryTrace(request);
    } catch (Exception e) {
      log.error("根据手机号查询圆通物流信息报错", e);
      throw SystemException.wrap(e, "根据手机号查询圆通物流信息报错");
    }

    // 3. Check & Convert: YTO DTO -> Domain Model
    if (!"1".equals(response.getCode())) {
      // 如果没查到，可能不算异常，视业务而定。这里假设抛错
      throw new BusinessException(OutboundErrorCode.NOT_FOUND, "YTO query failed, phone: " + phone + response.getMessage());
    }

    List<LogisticsNode> nodes = response.getData() == null
      ? Collections.emptyList()
      : response.getData().stream()
      .map(t -> new LogisticsNode(t.getUploadTime(), t.getProcessInfo()))
      .collect(Collectors.toList());

    // 统一返回标准模型
    return new LogisticsInfo(phone, LogisticsStatus.EXCEPTION, CargoType.FRESH, nodes);
  }

  @Override
  public LogisticsInfo getLogisticsByTrackingNo(String trackingNo) {
    // 1. Convert: Domain -> YTO DTO
    YtoRequest request = new YtoRequest();
    request.setWaybillNo(trackingNo);

    // 2. Call: Retrofit
    YtoResponse response;
    try {
      response = ytoClient.queryTrace(request);
    } catch (Exception e) {
      log.error("根据运单号查询圆通物流信息报错", e);
      throw SystemException.wrap(e, "根据运单号查询圆通物流信息报错");
    }

    // 3. Check & Convert: YTO DTO -> Domain Model
    if (!"1".equals(response.getCode())) {
      // 如果没查到，可能不算异常，视业务而定。这里假设抛错
      throw new BusinessException(OutboundErrorCode.NOT_FOUND, "YTO query failed, phone: " + trackingNo + response.getMessage());
    }

    List<LogisticsNode> nodes = response.getData() == null
      ? Collections.emptyList()
      : response.getData().stream()
      .map(t -> new LogisticsNode(t.getUploadTime(), t.getProcessInfo()))
      .collect(Collectors.toList());

    // 统一返回标准模型
    return new LogisticsInfo(trackingNo, LogisticsStatus.EXCEPTION, CargoType.FRESH, nodes);
  }
}
