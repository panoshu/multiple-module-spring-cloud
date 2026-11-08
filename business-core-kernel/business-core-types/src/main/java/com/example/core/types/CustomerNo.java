package com.example.core.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

/**
 * 客户编号
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 10:17
 */
@IdDefinition(type = IdType.ULID)
public record CustomerNo(String value) implements Identifier<String> {

  public CustomerNo {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("CustomerNo cannot be null or blank.");
    }
  }

  public static CustomerNo of(String value) {
    return new CustomerNo(value);
  }
}
