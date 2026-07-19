package com.example.file.application.usecase;

import com.example.file.application.command.CopyFileCommand;
import com.example.file.domain.gateway.CopyResult;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CopyFileUseCaseTest {

    private FileMetadataRepository metadataRepository;
    private FileStorageGateway storageGateway;
    private StorageTargetResolver targetResolver;
    private EventBus eventBus;
    private CopyFileUseCase useCase;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        storageGateway = mock(FileStorageGateway.class);
        targetResolver = mock(StorageTargetResolver.class);
        eventBus = mock(EventBus.class);
        useCase = new CopyFileUseCase(metadataRepository, storageGateway, targetResolver, eventBus);
    }

    @Test
    @DisplayName("copy 应创建新元数据并调用 gateway.copy")
    void copy_should_create_new_metadata_and_call_gateway_copy() {
        FileId srcFileId = new FileId("01H8SRC001");
        FileMetadata srcFile = FileMetadata.create(
            srcFileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("B001"),
            "oss-source", StorageType.OSS, UserNo.of("u1"), null
        );
        srcFile.markUploaded("storage/key", "md5hash");
        srcFile.clearDomainEvents();

        FileId newFileId = new FileId("01H8NEW001");
        when(metadataRepository.loadOrThrow(srcFileId)).thenReturn(srcFile);
        when(storageGateway.copy(eq(srcFileId), eq(FileUsage.EXPORT), any()))
            .thenReturn(new CopyResult(newFileId, "export/key"));
        when(targetResolver.resolveByUsage(FileUsage.EXPORT, "annuity"))
            .thenReturn(new StorageTarget(
                "oss-export", StorageType.OSS, "https://oss.example.com", "bucket",
                "base", null, "ak", "sk", java.util.Map.of()
            ));

        CopyFileCommand cmd = new CopyFileCommand(
            srcFileId, FileUsage.EXPORT, BatchId.of("B001"), UserNo.of("u1")
        );

        FileId result = useCase.copy(cmd);

        assertThat(result).isEqualTo(newFileId);
        verify(metadataRepository).save(any(FileMetadata.class));
        verify(eventBus, atLeastOnce()).publish(any());
    }
}
