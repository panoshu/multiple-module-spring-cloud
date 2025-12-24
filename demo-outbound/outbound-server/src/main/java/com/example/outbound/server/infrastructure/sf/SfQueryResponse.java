package com.example.outbound.server.infrastructure.sf;

import lombok.Data;

import java.util.List;

/**
 * SfQueryResponse
 *
 * @author YourName
 * @since 2025/12/14 21:31
 */
@Data
public class SfQueryResponse {
  private String status;
  private List<Route> routes;

  @Data
  public static class Route {
    private String time;
    private String address;
    private String remark;
  }
}
