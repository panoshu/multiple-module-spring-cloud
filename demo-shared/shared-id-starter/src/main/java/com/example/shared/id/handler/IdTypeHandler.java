package com.example.shared.id.handler;

import com.example.shared.id.metadata.IdDataType;
import com.example.shared.id.metadata.IdMeta;
import com.example.shared.id.strategy.IdGenerationStrategy;

import java.time.LocalDateTime;

/**
 * ID 类型处理器接口
 */
public interface IdTypeHandler {

  /**
   * 该处理器支持的数据类型
   */
  IdDataType getSupportedDataType();

  /**
   * 生成原始 ID 值
   */
  Object handle(IdMeta meta, IdGenerationStrategy strategy, String prefix, LocalDateTime now);
}
