package com.example.shared.identifier.contract;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/1/6 12:29
 */
public enum IdType {
  /**
   * 号段模式 (Segment / Auto-Increment)
   * 基于数据库号段，生成递增数字，支持格式化模板
   * 依赖: SequenceProvider (TinyId)
   */
  SEGMENT,

  /**
   * 雪花算法 (Snowflake)
   * 基于时间戳+机器ID+序列号，生成 Long 型唯一 ID
   * 依赖: 机器 ID 配置
   */
  SNOWFLAKE,

  /**
   * ULID (Universally Unique Lexicographically Sortable Identifier)
   * 26位字符，包含时间戳，可排序，不依赖中心节点
   */
  ULID,

  /**
   * UUID v7
   * 基于时间戳的 UUID，兼容 UUID 标准且通过时间排序，数据库索引友好
   */
  UUID_V7,

}
