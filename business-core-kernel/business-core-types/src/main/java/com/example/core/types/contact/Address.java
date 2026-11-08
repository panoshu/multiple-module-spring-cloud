package com.example.core.types.contact;

import java.util.Objects;

/**
 * 地址值对象
 *
 * @author panoshu
 * @since 2026/8/1
 */
public record Address(
  String country,
  String province,
  String city,
  String district,
  String detail
) {

  private static final int MAX_DETAIL_LENGTH = 256;

  public Address {
    country = normalize(country);
    province = normalize(province);
    city = normalize(city);
    district = normalize(district);
    detail = normalize(detail);

    Objects.requireNonNull(country, "Country required");
    Objects.requireNonNull(detail, "Address detail required");

    if (detail.length() > MAX_DETAIL_LENGTH) {
      throw new IllegalArgumentException("Address detail too long");
    }
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    return value.trim();
  }

  public String fullAddress() {
    return String.join(" ", country, province, city, district, detail);
  }
}
