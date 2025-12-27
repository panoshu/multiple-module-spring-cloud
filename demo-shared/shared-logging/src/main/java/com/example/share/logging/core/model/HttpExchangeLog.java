package com.example.share.logging.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * 这是一个纯 POJO，不再需要 @Entity、@Table 等 JPA 注解
 * 也不再需要 extends 任何类，保持最纯粹的数据结构
 */
@Data
@Accessors(chain = true)
public class HttpExchangeLog {

  // 基础信息
  private String correlationId; // 业务关联主键
  private OffsetDateTime createdTime; // 记录创建时间

  // 时间轴 (新增)
  private OffsetDateTime requestTime;  // 请求到达时间
  private OffsetDateTime responseTime; // 响应发出时间
  private Long durationMillis;        // 耗时

  // 请求信息
  private String method;
  private String uri;
  private String remote;
  private String requestHeaders;
  private String requestContent;
  private String contentType;

  // 响应信息
  private Integer statusCode;
  private String responseHeaders;
  private String responseContent;

  // 客户端信息
  private String clientInfo;
  private String ip;
  private String userAgent;

  // 状态标记
  private boolean truncated;
  private boolean complete;
}
