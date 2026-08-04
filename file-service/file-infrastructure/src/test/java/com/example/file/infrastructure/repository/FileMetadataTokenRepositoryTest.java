package com.example.file.infrastructure.repository;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.infrastructure.FileInfrastructureTestConfiguration;
import com.example.shared.identifier.id.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileMetadataRepository Token 字段读写集成测试（Task 13）。
 *
 * <p>验证 Task 5 改造后的 FileMetadata 聚合根（含 accessScope/digest/digestAlgorithm）
 * 经过 FileMetadataDO/Converter/RepositoryImpl 完整持久化往返后字段一致。
 *
 * <p>使用 H2 内存数据库 + MyBatis-Flex，复用 {@link FileInfrastructureTestConfiguration}
 * 限定 Spring 上下文只加载 repository/converter/mapper 包 Bean。
 *
 * <p>每个测试方法通过 @Sql 重建 t_file_metadata 表，保证用例间隔离。
 */
@SpringBootTest(classes = FileInfrastructureTestConfiguration.class)
@Sql(scripts = "/schema-file-metadata.sql")
@DisplayName("FileMetadataRepository Token 字段读写")
class FileMetadataTokenRepositoryTest {

  @Autowired
  private FileMetadataRepository repository;

  @Test
  @DisplayName("createForUpload 后保存并加载，accessScope 正确且 PENDING_UPLOAD 状态字段为 null")
  void should_save_and_load_with_access_scope() {
    FileId fileId = new FileId("f-token-test-001");
    FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
    FileMetadata file = FileMetadata.createForUpload(
      fileId, FileUsage.SOURCE, "biz", "test-app",
      new BatchId("b001"), scope, "target-001", StorageType.LOCAL,
      UserNo.of("u1"), LocalDateTime.now().plusDays(7)
    );
    repository.save(file);

    Optional<FileMetadata> loaded = repository.load(fileId);
    assertThat(loaded).isPresent();
    FileMetadata got = loaded.get();
    assertThat(got.accessScope()).isEqualTo(scope);
    assertThat(got.status()).isEqualTo(FileStatus.PENDING_UPLOAD);
    assertThat(got.originalName()).isNull();
    assertThat(got.size()).isNull();
    assertThat(got.storageKey()).isNull();
    assertThat(got.digest()).isNull();
    assertThat(got.digestAlgorithm()).isNull();
    assertThat(got.usage()).isEqualTo(FileUsage.SOURCE);
    assertThat(got.targetId()).isEqualTo("target-001");
    assertThat(got.storageType()).isEqualTo(StorageType.LOCAL);
    assertThat(got.businessBatchId()).isEqualTo(new BatchId("b001"));
    assertThat(got.createdBy()).isEqualTo(UserNo.of("u1"));
  }

  @Test
  @DisplayName("completeUpload 后保存，digest/digestAlgorithm/originalName/size 字段正确")
  void should_save_with_digest_after_complete_upload() {
    FileId fileId = new FileId("f-token-test-002");
    FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
    FileMetadata file = FileMetadata.createForUpload(
      fileId, FileUsage.SOURCE, "biz", "test-app",
      new BatchId("b001"), scope, "target-001", StorageType.LOCAL,
      UserNo.of("u1"), LocalDateTime.now().plusDays(7)
    );
    file.completeUpload("report.xlsx", 1024L, "application/xlsx",
      "storage-key-001", "sm3-digest-001");
    repository.save(file);

    Optional<FileMetadata> loaded = repository.load(fileId);
    assertThat(loaded).isPresent();
    FileMetadata got = loaded.get();
    assertThat(got.digest()).isEqualTo("sm3-digest-001");
    assertThat(got.digestAlgorithm()).isEqualTo("SM3");
    assertThat(got.originalName()).isEqualTo("report.xlsx");
    assertThat(got.size()).isEqualTo(1024L);
    assertThat(got.storageKey()).isEqualTo("storage-key-001");
    assertThat(got.contentType()).isEqualTo("application/xlsx");
    assertThat(got.status()).isEqualTo(FileStatus.UPLOADED);
    assertThat(got.uploadedAt()).isNotNull();
    // accessScope 仍保留
    assertThat(got.accessScope()).isEqualTo(scope);
  }

  @Test
  @DisplayName("load 不存在的 fileId 应返回 empty")
  void should_return_empty_when_load_non_existent() {
    Optional<FileMetadata> loaded = repository.load(new FileId("non-existent-id"));
    assertThat(loaded).isEmpty();
  }
}
