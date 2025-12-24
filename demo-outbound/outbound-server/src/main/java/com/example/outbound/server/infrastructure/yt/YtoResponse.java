package com.example.outbound.server.infrastructure.yt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * YtoResponse
 *
 * @author YourName
 * @since 2025/12/14 21:48
 */
@Data
public class YtoResponse {
  private String code;         // "1" 代表成功
  private String message;
  private List<Trace> data;

  @Data
  public static class Trace {
    @JsonProperty("upload_time")
    private String uploadTime;
    @JsonProperty("process_info")
    private String processInfo;
  }
}
