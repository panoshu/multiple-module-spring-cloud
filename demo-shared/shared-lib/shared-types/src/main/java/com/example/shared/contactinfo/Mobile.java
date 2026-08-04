package com.example.shared.contactinfo;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Mobile
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 18:05
 */
public record Mobile(
  String value
) {


  private static final int MAX_LENGTH = 16;


  private static final Pattern MOBILE_PATTERN =
    Pattern.compile(
      "^\\+[1-9]\\d{1,14}$"
    );


  public Mobile {


    Objects.requireNonNull(
      value,
      "Mobile must not be null"
    );


    value = value.trim();


    if (value.isBlank()) {

      throw new IllegalArgumentException(
        "Mobile must not be blank"
      );

    }


    if (value.length() > MAX_LENGTH) {

      throw new IllegalArgumentException(
        "Mobile length exceeds limit"
      );

    }


    if (!MOBILE_PATTERN.matcher(value).matches()) {

      throw new IllegalArgumentException(
        "Invalid mobile format"
      );

    }

  }


  /**
   * 脱敏
   */
  public String masked() {


    if (value.length() < 8) {

      return "****";

    }


    return value.substring(0, 4)
      + "****"
      + value.substring(
      value.length() - 4
    );

  }

}
