package com.example.shared.id.strategy;

import com.example.shared.identifier.contract.IdType;

public interface IdGenerationStrategy {
  /**
   * 支持的 ID 类型
   */
  IdType getSupportedType();

  /**
   * 生成 ID
   *
   * @param context 上下文 (包含 bizType, seqKey 等)
   * @return 原始 ID 字符串
   */
  String nextId(IdContext context);

  Long nextLongId(IdContext context);

  /**
   * 该策略生成的 ID 是否需要上层进行格式化 (如补零、加日期前缀)
   * Segment 通常为 true，UUID/ULID 通常为 false
   */
  default boolean supportFormatting() {
    return false;
  }

  // 上下文参数对象
  record IdContext(String bizType, String seqKey) {
    public String getKey() {
      return this.seqKey() != null ? this.seqKey() : this.bizType();
    }
  }
}
