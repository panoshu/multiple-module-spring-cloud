package com.example.core.domain.business.aggregate.valueobject;

import com.example.core.types.Channel;

/**
 * OperatorInfo
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 16:52
 */
public record OperatorInfo(
  Channel channel,
  String operatorId,
  String operatorName,
  boolean isProxy // 是否代办
) {
}
