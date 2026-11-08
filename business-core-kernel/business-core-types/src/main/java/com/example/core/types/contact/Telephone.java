package com.example.core.types.contact;

import java.util.Objects;

/**
 * 固定电话值对象
 *
 * @author panoshu
 * @since 2026/8/1
 */
public record Telephone(
  String areaCode,
  String number,
  String extension
) {

  public Telephone {
    Objects.requireNonNull(areaCode, "areaCode required");
    Objects.requireNonNull(number, "number required");
    validate(areaCode, number);
  }

  private static void validate(String areaCode, String number) {
    if (!areaCode.matches("\\d{3,4}")) {
      throw new IllegalArgumentException("Invalid telephone area code");
    }
    if (!number.matches("\\d{7,8}")) {
      throw new IllegalArgumentException("Invalid telephone number");
    }
  }

  public String formatted() {
    return areaCode + "-" + number;
  }
}
