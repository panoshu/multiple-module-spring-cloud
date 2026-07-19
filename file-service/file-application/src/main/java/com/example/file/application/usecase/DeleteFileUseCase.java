package com.example.file.application.usecase;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteFileUseCase {

    private final FileMetadataRepository metadataRepository;
    private final EventBus eventBus;

    @Transactional
    public void delete(FileId fileId, UserNo deletedBy) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        if (file.status() == FileStatus.DELETED) {
            log.debug("文件已删除，幂等返回: fileId={}", fileId);
            return;
        }
        file.markDeleted(deletedBy);
        metadataRepository.save(file);
        file.getDomainEvents().forEach(eventBus::publish);
        file.clearDomainEvents();
        log.info("文件已逻辑删除: fileId={}, deletedBy={}", fileId, deletedBy);
    }
}
