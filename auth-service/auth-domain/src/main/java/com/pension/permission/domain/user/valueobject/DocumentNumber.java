package com.pension.permission.domain.user.valueobject;

/**
 * DocumentNumber
 *
 * @author panoshu
 * @since 2026/8/1
 */
public record DocumentNumber(String value) {

  private static final int MAX_LENGTH = 64;

  public DocumentNumber {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Identity number empty");
    }
    value = value.trim();
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("Identity number length exceeds max limit: " + MAX_LENGTH);
    }
  }

  public String masked() {
    if (value.length() <= 8) {
      return "****";
    }
    return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
  }

  public int length() {
    return value.trim().length();
  }
}
