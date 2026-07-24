package com.example.shared.id.handler;

import com.example.shared.exception.SystemException;
import com.example.shared.id.errorcode.IdErrorCode;
import com.example.shared.id.metadata.IdDataType;
import com.example.shared.id.metadata.IdMeta;
import com.example.shared.id.strategy.IdGenerationStrategy;
import com.example.shared.id.strategy.IdGenerationStrategy.IdContext;

import java.time.LocalDateTime;

public class LongTypeHandler implements IdTypeHandler {

  @Override
  public IdDataType getSupportedDataType() {
    return IdDataType.LONG;
  }

  @Override
  public Object handle(IdMeta meta, IdGenerationStrategy strategy, String prefix, LocalDateTime now) {
    // 1. 计算 SeqKey
    String seqKey = meta.computeSeqKey(prefix, now);

    // 2. 生成原始 Long
    long rawVal = strategy.nextLongId(new IdContext(meta.baseName(), seqKey));

    // 3. 简单模式直接返回
    if (meta.isSimpleSequence()) {
      return rawVal;
    }

    // 4. 复杂模式 (format="%d%s")：格式化后校验是否为纯数字
    // 注意：这里逻辑稍微绕一点，是为了满足 "Long 类型 ID 也要支持日期前缀" 的特殊需求
    String formattedStr = meta.formatId(String.valueOf(rawVal), prefix, now);
    try {
      return Long.parseLong(formattedStr);
    } catch (NumberFormatException e) {
      throw new SystemException(IdErrorCode.ID_FORMAT_ERROR)
        .withLogDetail("Formatted ID '%s' is not a valid Long. Check @IdDefinition format.".formatted(formattedStr));
    }
  }
}
