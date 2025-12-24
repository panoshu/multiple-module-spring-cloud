package com.example.outbound.server.infrastructure.sf;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * SfQueryRequest
 *
 * @author YourName
 * @since 2025/12/14 21:30
 */
@Data
public class SfQueryRequest {
  @JsonProperty("tracking_number")
  private String trackingNumber;
  @JsonProperty("phone_check")
  private String phoneCheck;
}
