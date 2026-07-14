package com.example.approval.types;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 账管人编码
 *
 * @author approval-service
 */
public record AccountManagerCode(String value) implements ValueObject {

  public AccountManagerCode {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("AccountManagerCode cannot be null or blank");
    }
  }

  public static AccountManagerCode of(String value) {
    return new AccountManagerCode(value);
  }
}