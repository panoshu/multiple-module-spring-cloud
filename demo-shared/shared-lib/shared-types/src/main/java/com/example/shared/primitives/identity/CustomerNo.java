package com.example.shared.primitives.identity;


/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 10:17
 */
public record CustomerNo(String value) implements Identifier<String> {

  public CustomerNo {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("CustomerNo empty");
    }
  }

  public static CustomerNo of(String value) {
    return new CustomerNo(value);
  }
}
