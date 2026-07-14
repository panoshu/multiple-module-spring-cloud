package com.example.shared.web.trace.spi;

@FunctionalInterface
public interface ThrowableSupplier<T> {
  T get() throws Throwable;
}
