package com.example.file.application.usecase;

import com.example.file.application.command.StoreFileCommand;
import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.gateway.StoreResult;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.exception.SystemException;
import com.example.shared.id.algorithm.UlidAlgorithm;
import com.example.shared.identifier.id.FileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreFileUseCase {

  private final FileMetadataRepository metadataRepository;
  private final FileStorageGateway storageGateway;
  private final StorageTargetResolver targetResolver;

  @Transactional
  public FileId createMetadata(StoreFileCommand command) {
    FileId fileId = new FileId(UlidAlgorithm.generate());
    var target = targetResolver.resolveByUsage(command.usage(), command.bizType());
    FileMetadata file = FileMetadata.create(
      fileId,
      command.originalName(),
      command.size(),
      command.contentType(),
      command.usage(),
      command.bizType(),
      command.sourceApp(),
      command.businessBatchId(),
      target.targetId(),
      target.type(),
      command.uploadedBy(),
      command.expiresAt()
    );
    metadataRepository.save(file);
    log.info("文件元数据已创建: fileId={}, usage={}, bizType={}",
      fileId, command.usage(), command.bizType());
    return fileId;
  }

  @Transactional
  public void store(FileId fileId, InputStream content, long contentLength) {
    FileMetadata file = metadataRepository.loadOrThrow(fileId);
    if (file.status() != FileStatus.PENDING_UPLOAD) {
      throw new SystemException(FileErrorCodes.FILE_ALREADY_UPLOADED)
        .withLogDetail("fileId=" + fileId + ", 当前状态=" + file.status());
    }
    StoreResult result = storageGateway.store(fileId, content, contentLength);
    file.markUploaded(result.storageKey(), result.digest());
    metadataRepository.save(file);
    log.info("文件已存储: fileId={}, storageKey={}", fileId, result.storageKey());
  }
}
