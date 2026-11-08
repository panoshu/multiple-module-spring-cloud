package com.example.approval.domain.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 审批流名称值对象
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record FlowName(String value) implements ValueObject {

  public FlowName {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("审批流名称不能为空");
    }
    if (value.length() < 1 || value.length() > 100) {
      throw new IllegalArgumentException("审批流名称长度必须在1-100字符之间");
    }
  }

  /**
   * 静态工厂方法
   *
   * @param value 名称值
   * @return FlowName 实例
   */
  public static FlowName of(String value) {
    return new FlowName(value);
  }
}
