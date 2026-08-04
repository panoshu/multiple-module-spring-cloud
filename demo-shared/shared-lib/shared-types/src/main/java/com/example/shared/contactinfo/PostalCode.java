package com.example.shared.contactinfo;

import java.util.Objects;

/**
 * PostalCode
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 18:18
 */
public record PostalCode(

  String value

) {

  public PostalCode {


    Objects.requireNonNull(
      value
    );


    value = value.trim();


    if (!value.matches("\\d{6}")) {

      throw new IllegalArgumentException(
        "Invalid postal code"
      );
    }

  }

}
