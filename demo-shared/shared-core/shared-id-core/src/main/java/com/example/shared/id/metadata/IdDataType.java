package com.example.shared.id.metadata;

/**
 * ID 数据类型枚举
 * 用于在元数据层面标识 Identifier<K> 中的 K 的类型
 */
public enum IdDataType {
  /**
   * 字符串类型 (Identifier<String>)
   */
  STRING,

  /**
   * 长整型 (Identifier<Long>)
   */
  LONG,

  /**
   * 整型 (Identifier<Integer>) - 预留扩展
   */
  INTEGER
}
