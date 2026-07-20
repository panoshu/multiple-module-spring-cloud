package com.example.file.domain.repository;

import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.shared.domain.repository.Repository;
import com.example.shared.primitives.identity.FileAccessLogId;
import com.example.shared.primitives.identity.FileId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件访问流水仓储接口
 * <p>
 * 提供按文件、token 哈希、动作类型等维度的查询能力，用于审计与监控。
 */
public interface FileAccessLogRepository extends Repository<FileAccessLog, FileAccessLogId> {

    /**
     * 按文件 ID 查询所有访问流水
     */
    List<FileAccessLog> findByFileId(FileId fileId);

    /**
     * 按 token 哈希查询所有访问流水（同一 token 的 APPLY + ACCESS）
     */
    List<FileAccessLog> findByTokenHash(String tokenHash);

    /**
     * 按动作类型和时间范围统计流水数量
     */
    long countByActionAndTimeRange(FileAccessAction action, LocalDateTime from, LocalDateTime to);
}
