package com.example.approval.domain.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 节点顺序值对象
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record NodeOrder(int value) implements ValueObject, Comparable<NodeOrder> {

  public NodeOrder {
    if (value < 1) {
      throw new IllegalArgumentException("节点顺序必须大于等于1");
    }
  }

  /**
   * 静态工厂方法 - 第一个节点
   *
   * @return 第一个节点顺序(1)
   */
  public static NodeOrder first() {
    return new NodeOrder(1);
  }

  /**
   * 静态工厂方法
   *
   * @param value 顺序值
   * @return NodeOrder 实例
   */
  public static NodeOrder of(int value) {
    return new NodeOrder(value);
  }

  /**
   * 获取下一个节点顺序
   *
   * @return 下一个节点顺序
   */
  public NodeOrder next() {
    return new NodeOrder(this.value + 1);
  }

  /**
   * 是否为第一个节点
   *
   * @return true 如果是第一个节点
   */
  public boolean isFirst() {
    return this.value == 1;
  }

  @Override
  public int compareTo(NodeOrder other) {
    return Integer.compare(this.value, other.value);
  }
}
