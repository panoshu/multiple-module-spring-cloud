package com.example.shared.primitives.identity;


/**
 * 全局流水号
 * <p>
 * globalSequence = true: 强制所有业务共用 "GLOBAL_SEQ" 这个计数器
 * format = "%s": 格式为 序号 纯数字
 */
@IdDefinition(type = IdType.ULID)
public record SerialNo(String value) implements Identifier<String> {

  public SerialNo {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("SerialNo cannot be null or blank.");
    }
  }

  public static SerialNo of(String value) {
    return new SerialNo(value);
  }
}
