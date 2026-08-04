package com.example.file.domain.repository;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.domain.repository.Repository;
import com.example.shared.identifier.id.FileId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件元数据仓储接口
 */
public interface FileMetadataRepository extends Repository<FileMetadata, FileId> {

  /**
   * 按业务批次查询文件
   */
  List<FileMetadata> findByBusinessBatchId(String businessBatchId);

  /**
   * 按 usage + bizType 查询
   */
  List<FileMetadata> findByUsageAndBizType(FileUsage usage, String bizType);

  /**
   * 查询已过期但未删除的文件
   */
  List<FileMetadata> findExpiredBefore(LocalDateTime before);
}
