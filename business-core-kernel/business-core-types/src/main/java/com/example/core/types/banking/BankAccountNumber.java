package com.example.core.types.banking;

import java.util.Objects;

/**
 * BankAccountNumber
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 18:13
 */
public record BankAccountNumber(

  String value

) {

  public BankAccountNumber {

    Objects.requireNonNull(
      value,
      "account number required"
    );


    value = value.trim();


    if (!value.matches("\\d{10,32}")) {

      throw new IllegalArgumentException(
        "Invalid bank account number"
      );
    }

  }


  public String masked() {

    return value.substring(0, 4)
      + "****"
      + value.substring(
      value.length() - 4
    );

  }

}
