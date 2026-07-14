package com.example.file.domain.model.locator;

import java.util.Objects;

public record AbsoluteLocator(String cell) implements Locator {
  public AbsoluteLocator {
    Objects.requireNonNull(cell, "Absolute cell cannot be null");
  }
}
