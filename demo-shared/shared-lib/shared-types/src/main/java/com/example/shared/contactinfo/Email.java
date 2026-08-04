package com.example.shared.contactinfo;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Email
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 18:05
 */
public record Email(
  String value
) {


  private static final int MAX_LENGTH = 128;


  private static final Pattern EMAIL_PATTERN =
    Pattern.compile(
      "^[A-Za-z0-9._%+-]+@"
        + "[A-Za-z0-9.-]+\\."
        + "[A-Za-z]{2,}$"
    );


  public Email {


    Objects.requireNonNull(
      value,
      "Email must not be null"
    );


    value = value.trim()
      .toLowerCase(Locale.ROOT);


    if (value.isBlank()) {

      throw new IllegalArgumentException(
        "Email must not be blank"
      );

    }


    if (value.length() > MAX_LENGTH) {

      throw new IllegalArgumentException(
        "Email length exceeds limit"
      );

    }


    if (!EMAIL_PATTERN.matcher(value).matches()) {

      throw new IllegalArgumentException(
        "Invalid email format"
      );

    }

  }

}
