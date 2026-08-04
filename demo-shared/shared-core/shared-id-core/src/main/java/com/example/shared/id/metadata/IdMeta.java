package com.example.shared.id.metadata;

import com.example.shared.identifier.contract.IdType;
import lombok.extern.slf4j.Slf4j;

import java.lang.invoke.MethodHandle;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ID 元数据 (充血模型)
 * 包含配置信息，以及基于这些配置进行计算的核心逻辑
 */
@Slf4j
public record IdMeta(
  IdType type,
  IdDataType dataType,      // 【新增】数据类型枚举
  String baseName,
  String formatTemplate,
  String seqKeyTemplate,
  DateTimeFormatter dateFormatter,
  MethodHandle constructorHandle, // 【优化】使用 MethodHandle 替代反射 Constructor
  int seqLength,
  boolean isSimpleSequence,
  boolean hasDateInFormat,
  boolean hasPrefixInFormat,
  boolean hasDateInKey,
  boolean hasPrefixInKey
) {

  /**
   * 计算物理序列 Key (去数据库拿号的凭证)
   */
  public String computeSeqKey(String prefix, LocalDateTime now) {
    // 无状态 ID (UUID/ULID) 不需要计算复杂 Key
    if (!isSequenceType()) {
      return baseName;
    }

    // 模板: "%d_%n_%p"
    String key = seqKeyTemplate.replace("%n", baseName);

    if (hasPrefixInKey) {
      key = key.replace("%p", prefix == null ? "" : prefix);
    }
    if (hasDateInKey) {
      key = key.replace("%d", now.format(dateFormatter));
    }
    return key;
  }

  /**
   * 格式化最终 ID (仅针对 String 类型)
   */
  public String formatId(Object rawSeq, String prefix, LocalDateTime now) {
    // 1. 补零
    String rawSeqStr = rawSeq.toString();
    String seq = rawSeqStr;
    if (seqLength > 0 && rawSeqStr.length() < seqLength) {
      seq = "0".repeat(seqLength - rawSeqStr.length()) + rawSeqStr;
    }

    // 2. 简单模式直接返回
    if (isSimpleSequence) {
      return seq;
    }

    // 3. 模板替换
    // 支持 %s(序号), %n(BaseName), %p(前缀), %d(日期)
    String result = formatTemplate.replace("%s", seq)
      .replace("%n", baseName);

    if (hasPrefixInFormat) {
      result = result.replace("%p", prefix == null ? "" : prefix);
    }
    if (hasDateInFormat) {
      result = result.replace("%d", now.format(dateFormatter));
    }
    return result;
  }

  private boolean isSequenceType() {
    return type == IdType.SEGMENT || type == IdType.SNOWFLAKE;
  }
}
