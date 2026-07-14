package com.example.shared.primitives.identity;


/**
 * 批次号 ID
 * 定义：
 * 1. 基础名: BATCH (默认)
 * 2. 格式: 前缀 + 日期 + 序号
 */
@IdDefinition(format = "%p%d%s", seqKey = "%d_%n_%p", dateFormat = "yyyyMMdd")
public record BatchId(String value) implements Identifier<String> {
  public BatchId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("BatchId empty");
    }
  }

  public static BatchId of(String value) {
    return new BatchId(value);
  }
}
