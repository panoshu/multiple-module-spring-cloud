package com.example.outbound.server.infrastructure.yt;

import com.example.outbound.server.domain.logistics.LogisticsNode;
import com.example.shared.core.model.BaseExternalResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * YtoResponse
 *
 * @author YourName
 * @since 2025/12/14 21:48
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class YtoResponse extends BaseExternalResponse {
  private String code;    // 1: 成功
  private String message;
  private List<TraceInfo> data;
  private List<LogisticsNode> nodes;

  @Data
  public static class TraceInfo {
    private String uploadTime;
    private String processInfo;
  }
}
