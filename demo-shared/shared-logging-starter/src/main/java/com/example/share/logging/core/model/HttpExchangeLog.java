package com.example.share.logging.core.model;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.util.Assert;

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
  private String serviceName;
  private OffsetDateTime createdTime; // 记录创建时间

  // 时间轴
  private OffsetDateTime requestTime;  // 请求到达时间
  private OffsetDateTime responseTime; // 响应发出时间
  private Long durationMillis;        // 耗时

  // 请求信息
  private String method;
  private String uri;
  private String remote;
  private String contentType;
  @JsonRawValue
  private String requestHeaders;
  @JsonRawValue
  private String requestContent;

  // 响应信息
  private Integer statusCode;
  @JsonRawValue
  private String responseHeaders;
  @JsonRawValue
  private String responseContent;

  // 客户端信息
  private String clientInfo;
  private String ip;
  private String userAgent;

  // 状态标记
  private boolean truncated;
  private boolean complete;

  /**
   * 领域自检：校验核心状态是否合法
   *
   * @throws IllegalArgumentException 如果状态非法
   */
  public void validate() {
    Assert.hasText(correlationId, "correlationId must not be null or blank");
    // 可以在这里扩展其他业务规则校验
  }
}
