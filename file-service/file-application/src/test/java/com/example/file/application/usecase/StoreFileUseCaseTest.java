package com.example.file.application.usecase;

import com.example.file.application.command.StoreFileCommand;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.gateway.StoreResult;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.identifier.id.BatchId;
import com.example.shared.identifier.id.FileId;
import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StoreFileUseCaseTest {

  private FileMetadataRepository metadataRepository;
  private FileStorageGateway storageGateway;
  private StorageTargetResolver targetResolver;
  private StoreFileUseCase useCase;

  @BeforeEach
  void setUp() {
    metadataRepository = mock(FileMetadataRepository.class);
    storageGateway = mock(FileStorageGateway.class);
    targetResolver = mock(StorageTargetResolver.class);
    useCase = new StoreFileUseCase(metadataRepository, storageGateway, targetResolver);
  }

  @Test
  @DisplayName("createMetadata 应保存元数据")
  void createMetadata_should_save_metadata() {
    StoreFileCommand cmd = new StoreFileCommand(
      "test.xlsx", 1024, "application/octet-stream",
      FileUsage.SOURCE, "annuity", "business-core",
      BatchId.of("BATCH_001"), UserNo.of("u1"), null
    );
    StorageTarget target = new StorageTarget(
      "oss-source", StorageType.OSS, "https://oss.example.com", "bucket",
      "base", null, "ak", "sk", java.util.Map.of()
    );
    when(targetResolver.resolveByUsage(FileUsage.SOURCE, "annuity")).thenReturn(target);

    FileId fileId = useCase.createMetadata(cmd);

    assertThat(fileId).isNotNull();
    verify(metadataRepository).save(any(FileMetadata.class));
  }

  @Test
  @DisplayName("store 应调用 storageGateway.store 并标记 UPLOADED")
  void store_should_call_gateway_and_markUploaded() {
    FileId fileId = new FileId("01H8TESTFILE001");
    FileMetadata file = FileMetadata.create(
      fileId, "test.txt", 5, "text/plain",
      FileUsage.SOURCE, "annuity", "biz", BatchId.of("BATCH_001"),
      "oss-source", StorageType.OSS, UserNo.of("u1"), null
    );
    when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
    when(storageGateway.store(eq(fileId), any(), eq(5L)))
      .thenReturn(new StoreResult("test-storage-key", "test-md5"));

    useCase.store(fileId, new ByteArrayInputStream("hello".getBytes()), 5);

    assertThat(file.status()).isEqualTo(FileStatus.UPLOADED);
    assertThat(file.md5()).isEqualTo("test-md5");
    assertThat(file.storageKey()).isEqualTo("test-storage-key");
    verify(storageGateway).store(eq(fileId), any(), eq(5L));
    verify(metadataRepository).save(any(FileMetadata.class));
  }

  @Test
  @DisplayName("store 在非 PENDING_UPLOAD 状态时应抛异常")
  void store_should_throw_when_status_is_not_PENDING_UPLOAD() {
    FileId fileId = new FileId("01H8TESTFILE002");
    FileMetadata file = FileMetadata.create(
      fileId, "test.txt", 5, "text/plain",
      FileUsage.SOURCE, "annuity", "biz", BatchId.of("BATCH_001"),
      "oss-source", StorageType.OSS, UserNo.of("u1"), null
    );
    file.markUploaded("key", "md5");
    when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
      useCase.store(fileId, new ByteArrayInputStream("x".getBytes()), 1)
    ).isInstanceOf(RuntimeException.class);
  }
}
