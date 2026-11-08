package com.example.core.types.banking;

import java.util.Objects;

/**
 * BankBranchName
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 19:01
 */
public record BankBranchName(
  String value
) {


  private static final int MAX_LENGTH = 128;


  public BankBranchName {


    Objects.requireNonNull(
      value,
      "bank branch name required"
    );


    value = value.trim();


    if (value.isBlank()) {

      throw new IllegalArgumentException(
        "bank branch name empty"
      );
    }


    if (value.length() > MAX_LENGTH) {

      throw new IllegalArgumentException(
        "bank branch name too long"
      );
    }

  }

}
