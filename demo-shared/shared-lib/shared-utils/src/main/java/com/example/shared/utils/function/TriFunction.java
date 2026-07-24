package com.example.shared.utils.function;

/**
 * TriFunction
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/11 19:18
 */
@FunctionalInterface
public interface TriFunction<T, U, V, R> {
  R apply(T t, U u, V v);
}
