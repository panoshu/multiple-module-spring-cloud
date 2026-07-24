package com.example.shared.id.handler;

import com.example.shared.id.metadata.IdDataType;
import com.example.shared.id.metadata.IdMeta;
import com.example.shared.id.strategy.IdGenerationStrategy;
import com.example.shared.id.strategy.IdGenerationStrategy.IdContext;

import java.time.LocalDateTime;

public class StringTypeHandler implements IdTypeHandler {

  @Override
  public IdDataType getSupportedDataType() {
    return IdDataType.STRING;
  }

  @Override
  public Object handle(IdMeta meta, IdGenerationStrategy strategy, String prefix, LocalDateTime now) {
    // 1. 计算 SeqKey (只有支持格式化的策略才需要，如 Segment; UUID 不需要)
    String seqKey = strategy.supportFormatting() ? meta.computeSeqKey(prefix, now) : meta.baseName();

    // 2. 生成原始字符串
    String rawString = strategy.nextId(new IdContext(meta.baseName(), seqKey));

    // 3. 格式化 (如果策略允许)
    return strategy.supportFormatting() ? meta.formatId(rawString, prefix, now) : rawString;
  }
}
