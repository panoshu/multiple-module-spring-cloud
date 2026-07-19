package com.example.file.infrastructure.storage;

import com.example.shared.primitives.identity.FileId;

/**
 * 文件复制结果
 *
 * @param newFileId    新文件 ID
 * @param newStorageKey 新存储 key
 */
public record CopyResult(FileId newFileId, String newStorageKey) {
}
