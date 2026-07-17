package com.example.core.domain.aggregate.vauleobject;

import com.example.core.domain.aggregate.vauleobject.business.AnnuityChannel;
import com.example.shared.primitives.identity.UserNo;

/**
 * OperatorInfo
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 16:52
 */
public record OperatorInfo(
  AnnuityChannel channel,
  UserNo operatorId,
  String operatorName,
  boolean isProxy // 是否代办
) {
}
