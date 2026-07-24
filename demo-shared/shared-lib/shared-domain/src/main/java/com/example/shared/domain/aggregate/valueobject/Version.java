package com.example.shared.domain.aggregate.valueobject;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/13 17:22
 */
public record Version(long value) implements ValueObject, Comparable<Version> {

  public Version {
    if (value < 0) {
      throw new IllegalArgumentException("版本号不能为负数");
    }
  }

  public static Version initial() {
    return new Version(0L);
  }

  public static Version of(long value) {
    return new Version(value);
  }

  public Version next() {
    return new Version(this.value + 1);
  }

  public boolean isFirst() {
    return value == 0;
  }

  public boolean isAfter(Version other) {
    return value > other.value;
  }

  public boolean isBefore(Version other) {
    return value < other.value;
  }

  @Override
  public int compareTo(Version other) {
    return Long.compare(value, other.value);
  }
}
