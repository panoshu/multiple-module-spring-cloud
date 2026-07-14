package com.example.shared.primitives.identity;


/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 10:17
 */
public record PlanNo(String value) implements Identifier<String> {

  public PlanNo {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("CustomerNo empty");
    }
  }

  public static PlanNo of(String value) {
    return new PlanNo(value);
  }
}
