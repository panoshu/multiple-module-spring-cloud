package com.example.approval.domain.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 审批流版本号值对象
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record FlowVersion(int value) implements ValueObject, Comparable<FlowVersion> {

  public FlowVersion {
    if (value < 1) {
      throw new IllegalArgumentException("版本号必须大于等于1");
    }
  }

  /**
   * 静态工厂方法 - 初始版本
   *
   * @return 初始版本(1)
   */
  public static FlowVersion initial() {
    return new FlowVersion(1);
  }

  /**
   * 静态工厂方法
   *
   * @param value 版本号值
   * @return FlowVersion 实例
   */
  public static FlowVersion of(int value) {
    return new FlowVersion(value);
  }

  /**
   * 版本号递增
   *
   * @return 新版本号
   */
  public FlowVersion increment() {
    return new FlowVersion(this.value + 1);
  }

  @Override
  public int compareTo(FlowVersion other) {
    return Integer.compare(this.value, other.value);
  }
}
