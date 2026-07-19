package com.example.file.application.usecase;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeleteFileUseCaseTest {

    private FileMetadataRepository metadataRepository;
    private DeleteFileUseCase useCase;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        useCase = new DeleteFileUseCase(metadataRepository);
    }

    @Test
    @DisplayName("delete 应标记 DELETED")
    void delete_should_markDeleted() {
        FileId fileId = new FileId("01H8DEL001");
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("B001"),
            "local-1", StorageType.LOCAL, UserNo.of("u1"), null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        useCase.delete(fileId, UserNo.of("u1"));

        assertThat(file.status()).isEqualTo(FileStatus.DELETED);
        verify(metadataRepository).save(file);
    }

    @Test
    @DisplayName("delete 在已 DELETED 状态时应幂等返回")
    void delete_should_be_idempotent() {
        FileId fileId = new FileId("01H8DEL002");
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("B001"),
            "local-1", StorageType.LOCAL, UserNo.of("u1"), null
        );
        file.markDeleted(UserNo.of("u1"));
        file.clearDomainEvents();
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        useCase.delete(fileId, UserNo.of("u1"));

        // 不应再次保存
        verify(metadataRepository, never()).save(any());
    }
}
