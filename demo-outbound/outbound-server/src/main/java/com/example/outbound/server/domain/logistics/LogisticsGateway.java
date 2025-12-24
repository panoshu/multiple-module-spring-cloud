package com.example.outbound.server.domain.logistics;

/**
 * LogisticsGateway
 *
 * @author YourName
 * @since 2025/12/14 21:33
 */
public interface LogisticsGateway {

  /**
   * 策略判断：当前实现类是否支持该渠道
   */
  boolean supports(String channel);

  // 业务层只关心查物流，不关心是顺丰还是圆通，也不关心入参是 json 还是 xml
  LogisticsInfo getLogisticsByPhone(String phone);

  LogisticsInfo getLogisticsByTrackingNo(String trackingNo);
}
