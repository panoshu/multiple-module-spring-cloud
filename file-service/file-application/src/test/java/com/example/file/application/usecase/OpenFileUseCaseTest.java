package com.example.file.application.usecase;

import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OpenFileUseCaseTest {

    private FileMetadataRepository metadataRepository;
    private FileStorageGateway storageGateway;
    private OpenFileUseCase useCase;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        storageGateway = mock(FileStorageGateway.class);
        useCase = new OpenFileUseCase(metadataRepository, storageGateway);
    }

    @Test
    @DisplayName("open 在文件已删除时应抛异常")
    void open_should_throw_when_file_is_deleted() {
        FileId fileId = new FileId("01H8DEL001");
        FileMetadata file = FileMetadata.reconstitute(
            fileId, "test.txt", 5, "text/plain", "md5",
            "local-1", StorageType.LOCAL, "key",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("B001"),
            FileStatus.DELETED, UserNo.of("u1"), null, null,
            UserNo.of("u1"), UserNo.of("u1"), null, null, Version.initial()
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        assertThatThrownBy(() -> useCase.open(fileId))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("open 在文件已过期时应抛异常")
    void open_should_throw_when_file_is_expired() {
        FileId fileId = new FileId("01H8EXP001");
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("B001"),
            "local-1", StorageType.LOCAL, UserNo.of("u1"), LocalDateTime.now().minusHours(1)
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        assertThatThrownBy(() -> useCase.open(fileId))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("open 应返回 gateway 的流")
    void open_should_return_stream_from_gateway() {
        FileId fileId = new FileId("01H8OPEN001");
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("B001"),
            "local-1", StorageType.LOCAL, UserNo.of("u1"), null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(storageGateway.open(fileId)).thenReturn(new ByteArrayInputStream("hello".getBytes()));

        var stream = useCase.open(fileId);
        assertThat(stream).isNotNull();
    }
}
