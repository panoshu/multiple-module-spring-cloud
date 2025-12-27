package com.example.outbound.server.infrastructure.sf;

import com.example.outbound.server.domain.logistics.CargoType;
import com.example.outbound.server.domain.logistics.LogisticsNode;
import com.example.outbound.server.domain.logistics.LogisticsStatus;
import com.example.shared.core.model.BaseExternalResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * SfQueryResponse
 *
 * @author YourName
 * @since 2025/12/14 21:31
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SfQueryResponse extends BaseExternalResponse {
  private String head; // OK / ERR
  private String message;
  private CargoType type;
  private List<LogisticsNode> nodes;
  private LogisticsStatus  status;
  private List<TraceInfo> data;


  @Data
  public static class TraceInfo {
    private String uploadTime;
    private String processInfo;
  }
}
