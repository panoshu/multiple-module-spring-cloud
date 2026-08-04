package com.example.file.domain.gateway;

import com.example.shared.identifier.id.FileId;

/**
 * 文件复制结果
 *
 * @param newFileId     新文件 ID
 * @param newStorageKey 新存储 key
 */
public record CopyResult(FileId newFileId, String newStorageKey) {
}
