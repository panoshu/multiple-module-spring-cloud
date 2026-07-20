package com.example.file.application.usecase;

import com.example.file.application.service.FileAccessLogWriter;
import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.SessionUser;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
import com.example.shared.exception.SystemException;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("DownloadFileWithTokenUseCase")
class DownloadFileWithTokenUseCaseTest {

    private FileMetadataRepository metadataRepository;
    private FileTokenService tokenService;
    private FileStorageGateway storageGateway;
    private FileAccessLogRepository logRepository;
    private FileAccessLogWriter fileAccessLogWriter;
    private DownloadFileWithTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        tokenService = mock(FileTokenService.class);
        storageGateway = mock(FileStorageGateway.class);
        logRepository = mock(FileAccessLogRepository.class);
        fileAccessLogWriter = mock(FileAccessLogWriter.class);
        useCase = new DownloadFileWithTokenUseCase(metadataRepository, tokenService, storageGateway, logRepository, fileAccessLogWriter);
    }

    @Test
    @DisplayName("prepareDownload 正常流程: 解密 → load → verify → 写 SUCCESS 流水 → 返回 DownloadContext")
    void prepareDownload_should_succeed_and_return_context() {
        FileId fileId = new FileId("01H8TESTFILE001");
        FileMetadata file = newUploadedFile(fileId);
        FileTokenPayload payload = new FileTokenPayload(
            "tok-002", fileId, FileUsage.EXPORT, "annuity",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            null, null, LocalDateTime.now().plusMinutes(10)
        );
        when(tokenService.decrypt("encrypted-token")).thenReturn(payload);
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));

        DownloadFileWithTokenUseCase.DownloadContext ctx =
            useCase.prepareDownload("encrypted-token", session, "10.0.0.1");

        assertThat(ctx.fileId()).isEqualTo(fileId);
        assertThat(ctx.originalName()).isEqualTo("report.xlsx");
        assertThat(ctx.size()).isEqualTo(1024L);
        assertThat(ctx.contentType()).isEqualTo("application/xlsx");
        assertThat(ctx.digest()).isEqualTo("sm3-digest");
        // SUCCESS 流水仍直接由 prepareDownload 事务内的 logRepository.save 写入
        ArgumentCaptor<FileAccessLog> logCaptor = ArgumentCaptor.forClass(FileAccessLog.class);
        verify(logRepository).save(logCaptor.capture());
        FileAccessLog savedLog = logCaptor.getValue();
        assertThat(savedLog.tokenHash()).hasSize(64);
        // 不应触发 FAIL 流水写入
        verifyNoInteractions(fileAccessLogWriter);
    }

    @Test
    @DisplayName("prepareDownload token 解密失败: 抛异常且不写 ACCESS 流水")
    void prepareDownload_should_throw_without_log_when_decrypt_fails() {
        when(tokenService.decrypt("bad-token"))
            .thenThrow(new SystemException(FileErrorCodes.FILE_TOKEN_INVALID));

        SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));

        assertThatThrownBy(() -> useCase.prepareDownload("bad-token", session, "10.0.0.1"))
            .isInstanceOf(SystemException.class);
        verifyNoInteractions(logRepository, metadataRepository, storageGateway, fileAccessLogWriter);
    }

    @Test
    @DisplayName("prepareDownload verifyAndConsume 失败: 写 FAIL 流水并抛异常")
    void prepareDownload_should_write_fail_log_when_verify_fails() {
        FileId fileId = new FileId("01H8TESTFILE002");
        FileMetadata file = newUploadedFile(fileId);
        FileTokenPayload payload = new FileTokenPayload(
            "tok-002", fileId, FileUsage.EXPORT, "annuity",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            null, null, LocalDateTime.now().plusMinutes(10)
        );
        when(tokenService.decrypt("encrypted-token")).thenReturn(payload);
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(tokenService.verifyAndConsumeDownloadToken(eq("encrypted-token"), any(), eq(file)))
            .thenThrow(new SystemException(FileErrorCodes.FILE_TOKEN_ALREADY_USED));

        SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));

        assertThatThrownBy(() -> useCase.prepareDownload("encrypted-token", session, "10.0.0.1"))
            .isInstanceOf(SystemException.class);
        // FAIL 流水通过 FileAccessLogWriter 写入（独立 REQUIRES_NEW 事务）
        verify(fileAccessLogWriter).writeAccessLogFailed(eq(fileId), eq(payload), eq(session), eq("10.0.0.1"), eq("encrypted-token"), anyString());
        verifyNoInteractions(logRepository);
    }

    private FileMetadata newPendingFile(FileId fileId) {
        return FileMetadata.createForUpload(
            fileId, FileUsage.EXPORT, "annuity", "approval-service",
            new BatchId("b001"),
            new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
            "target-001", StorageType.LOCAL, UserNo.of("u1"),
            LocalDateTime.now().plusDays(7)
        );
    }

    private FileMetadata newUploadedFile(FileId fileId) {
        FileMetadata file = newPendingFile(fileId);
        file.completeUpload("report.xlsx", 1024L, "application/xlsx", "storage-key", "sm3-digest");
        return file;
    }
}
