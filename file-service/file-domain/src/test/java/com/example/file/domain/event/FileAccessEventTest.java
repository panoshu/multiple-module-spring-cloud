package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.identifier.id.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("文件访问领域事件")
class FileAccessEventTest {

  @Test
  @DisplayName("UploadTokenAppliedEvent.of 创建成功")
  void should_create_upload_token_applied_event() {
    FileMetadata file = newFileMetadata();
    UploadTokenAppliedEvent event = UploadTokenAppliedEvent.of(file, "hash-001", LocalDateTime.now().plusMinutes(15));
    assertThat(event.fileId()).isEqualTo(file.id());
    assertThat(event.tokenHash()).isEqualTo("hash-001");
    assertThat(event.eventId()).isNotNull();
    assertThat(event.occurredOn()).isNotNull();
  }

  @Test
  @DisplayName("FileUploadedWithTokenEvent.of 创建成功")
  void should_create_file_uploaded_with_token_event() {
    FileMetadata file = newUploadedMetadata();
    FileUploadedWithTokenEvent event = FileUploadedWithTokenEvent.of(file, "hash-001");
    assertThat(event.fileId()).isEqualTo(file.id());
    assertThat(event.tokenHash()).isEqualTo("hash-001");
    assertThat(event.digest()).isEqualTo("digest-001");
  }

  @Test
  @DisplayName("DownloadTokenAppliedEvent.of 创建成功")
  void should_create_download_token_applied_event() {
    FileMetadata file = newUploadedMetadata();
    DownloadTokenAppliedEvent event = DownloadTokenAppliedEvent.of(file, "hash-002", LocalDateTime.now().plusMinutes(15));
    assertThat(event.fileId()).isEqualTo(file.id());
    assertThat(event.tokenHash()).isEqualTo("hash-002");
  }

  @Test
  @DisplayName("FileDownloadedEvent.of 创建成功")
  void should_create_file_downloaded_event() {
    FileMetadata file = newUploadedMetadata();
    FileDownloadedEvent event = FileDownloadedEvent.of(file, "hash-003");
    assertThat(event.fileId()).isEqualTo(file.id());
    assertThat(event.tokenHash()).isEqualTo("hash-003");
  }

  private FileMetadata newFileMetadata() {
    return FileMetadata.createForUpload(
      new FileId("f001"), FileUsage.SOURCE, "import_declare", "approval-service",
      new BatchId("b001"),
      new FileAccessScope(CustomerNo.of("c001"), ProductNo.of("p001")),
      "target-001", StorageType.LOCAL,
      UserNo.of("u1"), LocalDateTime.now().plusDays(7)
    );
  }

  private FileMetadata newUploadedMetadata() {
    FileMetadata file = newFileMetadata();
    file.completeUpload("sample.xlsx", 100L, "application/xlsx",
      "storage-key-001", "digest-001");
    return file;
  }
}
