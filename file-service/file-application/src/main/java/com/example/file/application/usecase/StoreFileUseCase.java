package com.example.file.application.usecase;

import com.example.file.application.command.StoreFileCommand;
import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.SystemException;
import com.example.shared.id.algorithm.UlidAlgorithm;
import com.example.shared.primitives.identity.FileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreFileUseCase {

    private final FileMetadataRepository metadataRepository;
    private final FileStorageGateway storageGateway;
    private final StorageTargetResolver targetResolver;
    private final EventBus eventBus;

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
        file.getDomainEvents().forEach(eventBus::publish);
        file.clearDomainEvents();
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
        storageGateway.store(fileId, content, contentLength);
        String md5 = storageGateway.computeMd5(fileId);
        String storageKey = generateStorageKey(file);
        file.markUploaded(storageKey, md5);
        metadataRepository.save(file);
        file.getDomainEvents().forEach(eventBus::publish);
        file.clearDomainEvents();
        log.info("文件已存储: fileId={}, storageKey={}", fileId, storageKey);
    }

    private String generateStorageKey(FileMetadata file) {
        String datePartition = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return String.join("/",
            file.bizType() != null ? file.bizType() : "default",
            datePartition,
            file.businessBatchId() != null ? file.businessBatchId().value() : "no-batch",
            file.id().value(),
            file.originalName()
        );
    }
}
