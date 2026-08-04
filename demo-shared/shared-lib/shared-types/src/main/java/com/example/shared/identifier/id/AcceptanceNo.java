package com.example.shared.identifier.id;

import com.example.shared.identifier.contract.Identifier;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 10:17
 */
public record AcceptanceNo(String value) implements Identifier<String> {

  public AcceptanceNo {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("AcceptanceNo cannot be null or blank.");
    }
  }

  public static AcceptanceNo of(String value) {
    return new AcceptanceNo(value);
  }
}
