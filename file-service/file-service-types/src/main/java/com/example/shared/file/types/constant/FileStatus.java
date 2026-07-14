package com.example.shared.file.types.constant;

/**
 * 文件状态
 */
public enum FileStatus {
  /**
   * 临时状态 (上传后未被业务引用，24h后可清理)
   */
  TEMP,

  UPLOADING,

  UPLOAD_FAILED,

  /**
   * 废弃 (上传后被用户显式废弃)
   */
  DELETED,

  /**
   * 持久化状态 (已被业务引用，永久保存)
   */
  PERSISTENT,

  /**
   * 已归档 (冷存储)
   */
  ARCHIVED
}
