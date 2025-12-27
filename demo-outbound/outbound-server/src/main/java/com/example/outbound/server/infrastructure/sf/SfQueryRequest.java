package com.example.outbound.server.infrastructure.sf;

import com.example.shared.core.model.BaseExternalRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * SfQueryRequest
 *
 * @author YourName
 * @since 2025/12/14 21:30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class SfQueryRequest extends BaseExternalRequest<SfQueryRequest> {
  private String trackingNumber;
  private String checkPhoneNo;
  private String customerCode;
}
