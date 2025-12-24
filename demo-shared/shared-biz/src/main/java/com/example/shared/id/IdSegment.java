package com.example.shared.id;

/**
 * 号段对象：表示申请到的一段可用序列
 * 范围：[start, end]
 */
public record IdSegment(long start, long end) {
  public boolean isOver(long current) {
    return current > end;
  }
}
