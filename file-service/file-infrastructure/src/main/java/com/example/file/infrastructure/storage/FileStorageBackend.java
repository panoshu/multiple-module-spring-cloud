package com.example.file.infrastructure.storage;

import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;

import java.io.InputStream;

/**
 * 文件存储后端 SPI (基础设施层内部抽象)
 * <p>
 * 每个具体后端 (Local/OSS/NAS) 实现此接口。
 * FileStorageRouter 通过 target.type() 路由到对应实现。
 */
public interface FileStorageBackend {

    StorageType supportedType();

    void store(StorageTarget target, String storageKey,
               InputStream content, long contentLength);

    InputStream open(StorageTarget target, String storageKey);

    boolean exists(StorageTarget target, String storageKey);

    void copy(StorageTarget target, String srcKey, String dstKey);

    String computeDigest(StorageTarget target, String storageKey);
}
