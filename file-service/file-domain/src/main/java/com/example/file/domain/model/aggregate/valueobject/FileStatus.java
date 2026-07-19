package com.example.file.domain.model.aggregate.valueobject;

/**
 * 文件状态枚举
 * 状态机: PENDING_UPLOAD → UPLOADED → DELETED
 */
public enum FileStatus {
    PENDING_UPLOAD,
    UPLOADED,
    DELETED
}
