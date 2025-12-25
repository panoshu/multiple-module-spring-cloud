package com.example.share.logging.obfuscate.config;

import lombok.Getter;

import java.util.Arrays;

/**
 * 脱敏策略枚举，避免使用魔法字符串
 */
@Getter
public enum ObfuscationStrategyType {
  FULL("FULL"),
  PARTIAL_HIDE("PARTIAL_HIDE"),
  KEEP_FIRST_LAST("KEEP_FIRST_LAST"),
  HASH_SHA256("HASH_SHA256"),
  PATTERN_REGEX("PATTERN_REGEX"),

  ;

  private final String value;

  ObfuscationStrategyType(String value) {
    this.value = value;
  }

  /**
   * 将字符串转换为枚举，大小写不敏感
   */
  public static ObfuscationStrategyType fromString(String value) {
    if (value == null || value.isBlank()) {
      return FULL;
    }

    String normalized = value.trim().toUpperCase();
    return Arrays.stream(values())
      .filter(type -> type.name().equals(normalized) || type.value.equals(normalized))
      .findFirst()
      .orElse(FULL);
  }

  @Override
  public String toString() {
    return value;
  }
}
