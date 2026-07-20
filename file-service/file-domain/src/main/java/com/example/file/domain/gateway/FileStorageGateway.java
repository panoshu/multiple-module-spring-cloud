package com.example.file.domain.gateway;

import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;

import java.io.InputStream;

/**
 * 文件存储网关 SPI
 * <p>
 * 端口接口，由 FileStorageRouter 实现。
 * SPI 签名仅用 FileId，不暴露 storageKey/targetId。
 */
public interface FileStorageGateway {

    /**
     * 存储文件流到后端。
     * 调用前 FileMetadata 必须已 create() 并持久化（status=PENDING_UPLOAD）。
     * 返回 StoreResult，包含实际存储 key 和 MD5 校验值。
     */
    StoreResult store(FileId fileId, InputStream content, long contentLength);

    /**
     * 打开文件流。
     * 调用方必须 try-with-resources 关闭流。
     */
    InputStream open(FileId fileId);

    /**
     * 判断文件是否存在于存储后端
     */
    boolean exists(FileId fileId);

    /**
     * 复制文件到新用途对应的目标。
     * 返回 CopyResult (新 FileId + 新 storageKey)。
     */
    CopyResult copy(FileId srcFileId, FileUsage targetUsage, BatchId businessBatchId);

    /**
     * 计算文件摘要（SM3）
     */
    String computeDigest(FileId fileId);
}
