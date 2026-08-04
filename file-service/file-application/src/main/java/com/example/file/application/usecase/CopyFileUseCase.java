package com.example.file.application.usecase;

import com.example.file.application.command.CopyFileCommand;
import com.example.file.domain.gateway.CopyResult;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.identifier.id.FileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CopyFileUseCase {

  private final FileMetadataRepository metadataRepository;
  private final FileStorageGateway storageGateway;
  private final StorageTargetResolver targetResolver;

  @Transactional
  public FileId copy(CopyFileCommand command) {
    FileMetadata srcFile = metadataRepository.loadOrThrow(command.srcFileId());

    CopyResult copyResult = storageGateway.copy(
      command.srcFileId(), command.targetUsage(), command.businessBatchId()
    );

    var dstTarget = targetResolver.resolveByUsage(command.targetUsage(), srcFile.bizType());
    FileMetadata newFile = FileMetadata.create(
      copyResult.newFileId(),
      srcFile.originalName(),
      srcFile.size(),
      srcFile.contentType(),
      command.targetUsage(),
      srcFile.bizType(),
      srcFile.sourceApp(),
      command.businessBatchId(),
      dstTarget.targetId(),
      dstTarget.type(),
      command.operatedBy(),
      null
    );
    newFile.markUploaded(copyResult.newStorageKey(), srcFile.md5());
    metadataRepository.save(newFile);

    log.info("文件已复制: srcFileId={}, newFileId={}, targetUsage={}",
      command.srcFileId(), copyResult.newFileId(), command.targetUsage());
    return copyResult.newFileId();
  }
}
