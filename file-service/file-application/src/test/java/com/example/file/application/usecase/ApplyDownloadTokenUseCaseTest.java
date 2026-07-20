package com.example.file.application.usecase;

import com.example.file.application.command.ApplyDownloadTokenCommand;
import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
import com.example.shared.domain.errorcode.SharedDomainErrorCode;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ApplyDownloadTokenUseCase")
class ApplyDownloadTokenUseCaseTest {

    private FileMetadataRepository metadataRepository;
    private FileTokenService tokenService;
    private FileAccessLogRepository logRepository;
    private ApplyDownloadTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        tokenService = mock(FileTokenService.class);
        logRepository = mock(FileAccessLogRepository.class);
        useCase = new ApplyDownloadTokenUseCase(metadataRepository, tokenService, logRepository);
    }

    @Test
    @DisplayName("apply 正常流程: load → verifyDownloadable → 生成 token → 写 APPLY 流水")
    void apply_should_load_verify_generate_token_and_write_log() {
        FileId fileId = new FileId("01H8TESTFILE001");
        FileMetadata file = newUploadedFile(fileId);
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(tokenService.generateDownloadToken(eq(file), eq(Duration.ofMinutes(15))))
            .thenReturn("download-token");

        ApplyDownloadTokenCommand cmd = new ApplyDownloadTokenCommand(
            fileId, "approval-service",
            new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
            UserNo.of("u1"), Duration.ofMinutes(15)
        );

        String token = useCase.apply(cmd);

        assertThat(token).isEqualTo("download-token");
        ArgumentCaptor<FileAccessLog> logCaptor = ArgumentCaptor.forClass(FileAccessLog.class);
        verify(logRepository).save(logCaptor.capture());
        FileAccessLog savedLog = logCaptor.getValue();
        assertThat(savedLog.tokenHash()).hasSize(64);
    }

    @Test
    @DisplayName("apply 在 ttl 为空时应抛 DomainException(INVALID_OPERATION)")
    void apply_should_throw_when_ttl_is_null() {
        FileId fileId = new FileId("01H8TESTFILE001");
        ApplyDownloadTokenCommand cmd = new ApplyDownloadTokenCommand(
            fileId, "approval-service",
            new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
            UserNo.of("u1"), null
        );

        assertThatThrownBy(() -> useCase.apply(cmd))
            .isInstanceOf(DomainException.class)
            .matches(ex -> ((DomainException) ex).code().equals(SharedDomainErrorCode.INVALID_OPERATION.code()));
        verifyNoInteractions(metadataRepository, logRepository, tokenService);
    }

    @Test
    @DisplayName("apply 文件未上传时应抛 DomainException (verifyDownloadable 抛出)")
    void apply_should_throw_when_file_not_uploaded() {
        FileId fileId = new FileId("01H8TESTFILE002");
        FileMetadata file = newPendingFile(fileId);
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        ApplyDownloadTokenCommand cmd = new ApplyDownloadTokenCommand(
            fileId, "approval-service",
            new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
            UserNo.of("u1"), Duration.ofMinutes(15)
        );

        assertThatThrownBy(() -> useCase.apply(cmd))
            .isInstanceOf(DomainException.class);
        verifyNoInteractions(tokenService, logRepository);
    }

    private FileMetadata newPendingFile(FileId fileId) {
        return FileMetadata.createForUpload(
            fileId, FileUsage.SOURCE, "annuity", "approval-service",
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
