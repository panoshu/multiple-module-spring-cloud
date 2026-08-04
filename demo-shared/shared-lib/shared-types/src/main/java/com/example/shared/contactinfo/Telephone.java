package com.example.shared.contactinfo;

import java.util.Objects;

/**
 * Telephone
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 18:12
 */
public record Telephone(

  String areaCode,

  String number,

  String extension

) {


  public Telephone {


    Objects.requireNonNull(
      areaCode,
      "areaCode required"
    );


    Objects.requireNonNull(
      number,
      "number required"
    );


    validate(areaCode, number);

  }


  private static void validate(
    String areaCode,
    String number
  ) {

    if (!areaCode.matches("\\d{3,4}")) {

      throw new IllegalArgumentException(
        "Invalid telephone area code"
      );
    }


    if (!number.matches("\\d{7,8}")) {

      throw new IllegalArgumentException(
        "Invalid telephone number"
      );
    }

  }


  public String formatted() {

    return areaCode
      + "-"
      + number;

  }


}
