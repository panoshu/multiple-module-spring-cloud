package com.example.outbound.server.infrastructure.yt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * YtoRequest
 *
 * @author YourName
 * @since 2025/12/14 21:48
 */
@Data
public class YtoRequest {
  @JsonProperty("waybill_No") // 圆通可能叫 waybill_No
  private String waybillNo;

  @JsonProperty("phone_no")
  private String phoneNo;
}
