package com.example.auth.types;

import com.example.shared.identifier.contract.Identifier;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 菜单编码，冒号分隔的稳定业务标识。
 * <p>分配时确定，之后不可变。前端用作稳定引用 key。
 * <p>命名规范：小写字母 + 冒号分隔，反映树形结构。
 * 示例：{@code annuity}、{@code annuity:batch}、{@code annuity:batch:submit}、{@code system:user}
 */
public record MenuCode(String value) implements Identifier<String> {
  private static final Pattern VALID_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(?::[a-z][a-z0-9]*)*$");

  public MenuCode {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("MenuCode cannot be blank");
    }
    if (!VALID_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
        "MenuCode must be lowercase colon-separated (e.g., annuity:batch:submit)");
    }
  }
}
