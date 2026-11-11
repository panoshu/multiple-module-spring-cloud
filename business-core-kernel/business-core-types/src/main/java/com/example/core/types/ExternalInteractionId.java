package com.example.core.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

/**
 * 外部协作记录 ID。
 * <p>
 * 对应 §16.3：新增外部协作表使用 BIGINT 主键，由 {@code IdService} 号段策略生成。
 */
@IdDefinition(type = IdType.SEGMENT)
public record ExternalInteractionId(Long value) implements Identifier<Long> {

  public ExternalInteractionId {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException("ExternalInteractionId must be positive");
    }
  }

  public static ExternalInteractionId of(Long value) {
    return new ExternalInteractionId(value);
  }
}
