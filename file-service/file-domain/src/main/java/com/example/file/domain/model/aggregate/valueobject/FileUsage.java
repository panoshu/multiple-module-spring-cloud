package com.example.file.domain.model.aggregate.valueobject;

/**
 * 文件用途枚举，用于路由到对应的 StorageTarget
 */
public enum FileUsage {
    SOURCE,
    PARSED,
    EXPORT,
    ARCHIVE
}
