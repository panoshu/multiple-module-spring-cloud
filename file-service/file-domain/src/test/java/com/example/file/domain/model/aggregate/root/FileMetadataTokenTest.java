package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileMetadata Token 访问改造")
class FileMetadataTokenTest {

  @Test
  @DisplayName("createForUpload 创建 PENDING_UPLOAD 状态，文件信息为 null")
  void should_create_for_upload_with_pending_status() {
    FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
    FileMetadata file = FileMetadata.createForUpload(
      new FileId("f001"), FileUsage.SOURCE, "import_declare", "approval-service",
      new BatchId("b001"), scope, "target-001", StorageType.LOCAL,
      UserNo.of("u1"), LocalDateTime.now().plusDays(7)
    );
    assertThat(file.status()).isEqualTo(FileStatus.PENDING_UPLOAD);
    assertThat(file.originalName()).isNull();
    assertThat(file.size()).isNull();
    assertThat(file.contentType()).isNull();
    assertThat(file.storageKey()).isNull();
    assertThat(file.accessScope()).isEqualTo(scope);
  }

  @Test
  @DisplayName("completeUpload 设置文件信息并转 UPLOADED 状态")
  void should_complete_upload() {
    FileMetadata file = newPendingFile();
    file.completeUpload(
      "report.xlsx", 1024L, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "storage-key-001", "sm3-digest-001"
    );
    assertThat(file.status()).isEqualTo(FileStatus.UPLOADED);
    assertThat(file.originalName()).isEqualTo("report.xlsx");
    assertThat(file.size()).isEqualTo(1024L);
    assertThat(file.digest()).isEqualTo("sm3-digest-001");
    assertThat(file.digestAlgorithm()).isEqualTo("SM3");
  }

  @Test
  @DisplayName("completeUpload 在非 PENDING_UPLOAD 状态抛异常")
  void should_throw_when_complete_upload_in_wrong_status() {
    FileMetadata file = newPendingFile();
    file.completeUpload("n.xlsx", 1L, "text/plain", "k", "d");
    assertThatThrownBy(() -> file.completeUpload("n.xlsx", 1L, "text/plain", "k", "d"))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("verifyDownloadable 在 UPLOADED 状态通过")
  void should_verify_downloadable_when_uploaded() {
    FileMetadata file = newUploadedFile();
    file.verifyDownloadable();  // 不抛异常
  }

  @Test
  @DisplayName("verifyDownloadable 在 PENDING_UPLOAD 状态抛异常")
  void should_throw_when_verify_downloadable_pending() {
    FileMetadata file = newPendingFile();
    assertThatThrownBy(file::verifyDownloadable).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("verifyDownloadable 在过期时抛异常")
  void should_throw_when_verify_downloadable_expired() {
    FileMetadata file = FileMetadata.createForUpload(
      new FileId("f001"), FileUsage.SOURCE, "biz", "app",
      new BatchId("b001"),
      new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
      "target-001", StorageType.LOCAL, UserNo.of("u1"),
      LocalDateTime.now().minusDays(1)  // 已过期
    );
    assertThatThrownBy(file::verifyDownloadable).isInstanceOf(DomainException.class);
  }

  private FileMetadata newPendingFile() {
    return FileMetadata.createForUpload(
      new FileId("f001"), FileUsage.SOURCE, "import_declare", "approval-service",
      new BatchId("b001"),
      new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
      "target-001", StorageType.LOCAL, UserNo.of("u1"),
      LocalDateTime.now().plusDays(7)
    );
  }

  private FileMetadata newUploadedFile() {
    FileMetadata file = newPendingFile();
    file.completeUpload("report.xlsx", 1024L,
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "storage-key-001", "sm3-digest-001");
    return file;
  }
}
