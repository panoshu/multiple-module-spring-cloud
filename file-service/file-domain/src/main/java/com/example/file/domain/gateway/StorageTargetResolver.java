package com.example.file.domain.gateway;

import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;

import java.util.List;

/**
 * 存储目标解析器 SPI
 * <p>
 * 将 FileUsage 路由到具体的 StorageTarget。
 */
public interface StorageTargetResolver {

    /**
     * 按 FileUsage 路由到具体的 StorageTarget
     */
    StorageTarget resolveByUsage(FileUsage usage, String bizType);

    /**
     * 按 targetId 直接查询
     */
    StorageTarget resolveById(String targetId);

    /**
     * 列出所有配置的 StorageTarget
     */
    List<StorageTarget> listAll();
}
