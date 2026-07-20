package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.event.FileMetadataCreatedEvent;
import com.example.file.domain.event.FileDeletedEvent;
import com.example.file.domain.event.FileUploadedEvent;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileMetadataTest {

    private static final FileId TEST_FILE_ID = new FileId("01H8TESTFILEID000001");
    private static final BatchId TEST_BATCH_ID = BatchId.of("BATCH_TEST_001");
    private static final UserNo TEST_USER = UserNo.of("user001");

    @Test
    @DisplayName("create 应将状态设为 PENDING_UPLOAD 并注册 FileMetadataCreatedEvent")
    void create_should_set_status_to_PENDING_UPLOAD_and_register_event() {
        FileMetadata file = FileMetadata.create(
            TEST_FILE_ID, "test.xlsx", 1024, "application/vnd.ms-excel",
            FileUsage.SOURCE, "annuity", "business-core", TEST_BATCH_ID,
            "oss-source", StorageType.OSS, TEST_USER, null
        );

        assertThat(file.status()).isEqualTo(FileStatus.PENDING_UPLOAD);
        assertThat(file.id()).isEqualTo(TEST_FILE_ID);
        assertThat(file.originalName()).isEqualTo("test.xlsx");
        assertThat(file.size()).isEqualTo(1024);
        assertThat(file.businessBatchId()).isEqualTo(TEST_BATCH_ID);

        assertThat(file.getDomainEvents())
            .hasSize(1)
            .first()
            .isInstanceOf(FileMetadataCreatedEvent.class);
    }

    @Test
    @DisplayName("markUploaded 应将状态流转到 UPLOADED 并注册 FileUploadedEvent")
    void markUploaded_should_transition_to_UPLOADED() {
        FileMetadata file = newPendingFile();

        file.markUploaded("annuity/2026-07-19/BATCH_TEST_001/01H8.../test.xlsx", "d41d8cd98f00b204e9800998ecf8427e");

        assertThat(file.status()).isEqualTo(FileStatus.UPLOADED);
        assertThat(file.storageKey()).isEqualTo("annuity/2026-07-19/BATCH_TEST_001/01H8.../test.xlsx");
        assertThat(file.md5()).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
        assertThat(file.uploadedAt()).isNotNull();

        assertThat(file.getDomainEvents()).anyMatch(e -> e instanceof FileUploadedEvent);
    }

    @Test
    @DisplayName("markUploaded 在非 PENDING_UPLOAD 状态时应抛异常")
    void markUploaded_should_throw_when_status_is_not_PENDING_UPLOAD() {
        FileMetadata file = newUploadedFile();

        assertThatThrownBy(() -> file.markUploaded("key", "md5"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("markUploaded 在 storageKey 为空时应抛异常")
    void markUploaded_should_throw_when_storageKey_is_blank() {
        FileMetadata file = newPendingFile();

        assertThatThrownBy(() -> file.markUploaded("  ", "md5"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("markDeleted 应将状态流转到 DELETED 并注册 FileDeletedEvent")
    void markDeleted_should_transition_to_DELETED() {
        FileMetadata file = newUploadedFile();

        file.markDeleted(TEST_USER);

        assertThat(file.status()).isEqualTo(FileStatus.DELETED);
        assertThat(file.getDomainEvents()).anyMatch(e -> e instanceof FileDeletedEvent);
    }

    @Test
    @DisplayName("markDeleted 在已是 DELETED 状态时应幂等返回")
    void markDeleted_should_be_idempotent() {
        FileMetadata file = newUploadedFile();
        file.markDeleted(TEST_USER);
        int eventCountBefore = file.getDomainEvents().size();

        file.markDeleted(TEST_USER);

        assertThat(file.status()).isEqualTo(FileStatus.DELETED);
        assertThat(file.getDomainEvents()).hasSize(eventCountBefore);
    }

    @Test
    @DisplayName("isExpired 在 expiresAt 为 null 时应返回 false")
    void isExpired_should_return_false_when_expiresAt_is_null() {
        FileMetadata file = newPendingFile();
        assertThat(file.isExpired()).isFalse();
    }

    @Test
    @DisplayName("isExpired 在 expiresAt 为过去时间时应返回 true")
    void isExpired_should_return_true_when_expiresAt_is_past() {
        FileMetadata file = FileMetadata.create(
            TEST_FILE_ID, "test.xlsx", 1024, "application/octet-stream",
            FileUsage.SOURCE, "annuity", "business-core", TEST_BATCH_ID,
            "oss-source", StorageType.OSS, TEST_USER, LocalDateTime.now().minusHours(1)
        );
        assertThat(file.isExpired()).isTrue();
    }

    @Test
    @DisplayName("reconstitute 应恢复所有字段")
    void reconstitute_should_restore_all_fields() {
        LocalDateTime now = LocalDateTime.now();
        FileMetadata file = FileMetadata.reconstitute(
            TEST_FILE_ID, "test.xlsx", 1024L, "application/octet-stream", "md5hash",
            null, null, null,
            "oss-source", StorageType.OSS, "storage/key",
            FileUsage.SOURCE, "annuity", "business-core", TEST_BATCH_ID,
            FileStatus.UPLOADED, TEST_USER, now, null,
            TEST_USER, TEST_USER, now, now, null
        );

        assertThat(file.id()).isEqualTo(TEST_FILE_ID);
        assertThat(file.status()).isEqualTo(FileStatus.UPLOADED);
        assertThat(file.storageKey()).isEqualTo("storage/key");
        assertThat(file.md5()).isEqualTo("md5hash");
    }

    private FileMetadata newPendingFile() {
        return FileMetadata.create(
            TEST_FILE_ID, "test.xlsx", 1024, "application/vnd.ms-excel",
            FileUsage.SOURCE, "annuity", "business-core", TEST_BATCH_ID,
            "oss-source", StorageType.OSS, TEST_USER, null
        );
    }

    private FileMetadata newUploadedFile() {
        FileMetadata file = newPendingFile();
        file.markUploaded("storage/key", "md5hash");
        file.clearDomainEvents();
        return file;
    }
}
