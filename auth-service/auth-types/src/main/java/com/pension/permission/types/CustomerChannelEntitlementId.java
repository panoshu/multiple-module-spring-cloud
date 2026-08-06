package com.pension.permission.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

import java.util.Objects;

/**
 * 客户渠道开通记录 ID.
 *
 * @IdDefinition 声明 ULID 生成策略，由 shared-id-starter 在持久化时生成。
 */
@IdDefinition(type = IdType.ULID)
public record CustomerChannelEntitlementId(String value) implements Identifier<String> {
  public CustomerChannelEntitlementId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("CustomerChannelEntitlementId cannot be blank.");
    }
  }
}
