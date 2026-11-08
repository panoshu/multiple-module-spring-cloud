package com.example.file.domain.model.storage.valueobject;

/**
 * 文件用途枚举，用于路由到对应的 StorageTarget
 */
public enum FileUsage {
  SOURCE,
  PARSED,
  EXPORT,
  ARCHIVE
}
