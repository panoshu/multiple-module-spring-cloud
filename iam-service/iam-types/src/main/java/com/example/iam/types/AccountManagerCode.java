package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

import java.util.Objects;

/**
 * 账管人编号
 *
 * <p>账管人作为外部系统管理的数据，通过防腐层 Gateway 查询使用</p>
 *
 * @author iam-service
 * @since 2026/7/25
 */
public record AccountManagerCode(String value) implements Identifier<String> {

  public AccountManagerCode {
    Objects.requireNonNull(value, "AccountManagerCode value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("AccountManagerCode value cannot be blank");
    }
  }

  public static AccountManagerCode of(String value) {
    return new AccountManagerCode(value);
  }

  @Override
  public String toString() {
    return "AccountManagerCode{" + value + "}";
  }
}
