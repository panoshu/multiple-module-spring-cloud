package com.example.file.application.usecase;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.exception.SystemException;
import com.example.shared.primitives.identity.FileId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class OpenFileUseCase {

    private final FileMetadataRepository metadataRepository;
    private final FileStorageGateway storageGateway;

    @Transactional(readOnly = true)
    public InputStream open(FileId fileId) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        if (file.status() == FileStatus.DELETED) {
            throw new SystemException(FileErrorCodes.FILE_METADATA_NOT_FOUND)
                .withLogDetail("fileId=" + fileId + " 已删除");
        }
        if (file.isExpired()) {
            throw new SystemException(FileErrorCodes.FILE_EXPIRED)
                .withLogDetail("fileId=" + fileId);
        }
        return storageGateway.open(fileId);
    }

    @Transactional(readOnly = true)
    public FileMetadata loadMetadata(FileId fileId) {
        return metadataRepository.loadOrThrow(fileId);
    }
}
