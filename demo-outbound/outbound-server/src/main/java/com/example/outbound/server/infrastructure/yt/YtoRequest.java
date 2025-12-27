package com.example.outbound.server.infrastructure.yt;

import com.example.shared.core.model.BaseExternalRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * YtoRequest
 *
 * @author YourName
 * @since 2025/12/14 21:48
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class YtoRequest extends BaseExternalRequest<YtoRequest> {
  private String waybillNo; // 运单号
  private String phoneNo;
  private String userId;    // 圆通分配的ID
  private String appKey;    // 密钥
}
