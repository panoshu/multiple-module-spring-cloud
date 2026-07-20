package com.example.file.application.usecase;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StoreResult;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("UploadFileWithTokenUseCase")
class UploadFileWithTokenUseCaseTest {

    private FileMetadataRepository metadataRepository;
    private FileTokenService tokenService;
    private FileStorageGateway storageGateway;
    private FileAccessLogRepository logRepository;
    private UploadFileWithTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        tokenService = mock(FileTokenService.class);
        storageGateway = mock(FileStorageGateway.class);
        logRepository = mock(FileAccessLogRepository.class);
        useCase = new UploadFileWithTokenUseCase(metadataRepository, tokenService, storageGateway, logRepository);
    }

    @Test
    @DisplayName("upload 正常流程: 解密 → load → verify → store → completeUpload → 写 SUCCESS 流水")
    void upload_should_succeed_and_write_success_log() {
        FileId fileId = new FileId("01H8TESTFILE001");
        FileMetadata file = newPendingFile(fileId);
        FileTokenPayload payload = new FileTokenPayload(
            "tok-001", fileId, FileUsage.SOURCE, "annuity",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(10)
        );
        when(tokenService.decrypt("encrypted-token")).thenReturn(payload);
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(storageGateway.store(eq(fileId), any(), eq(5L)))
            .thenReturn(new StoreResult("storage-key-001", "sm3-digest"));

        MultipartFile multipart = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
        SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));

        FileId result = useCase.upload("encrypted-token", session, multipart, "10.0.0.1");

        assertThat(result).isEqualTo(fileId);
        verify(metadataRepository).save(any(FileMetadata.class));
        ArgumentCaptor<FileAccessLog> logCaptor = ArgumentCaptor.forClass(FileAccessLog.class);
        verify(logRepository).save(logCaptor.capture());
        FileAccessLog savedLog = logCaptor.getValue();
        assertThat(savedLog.tokenHash()).hasSize(64);
    }

    @Test
    @DisplayName("upload token 解密失败: 抛异常且不写 ACCESS 流水（无 fileId）")
    void upload_should_throw_without_log_when_decrypt_fails() {
        when(tokenService.decrypt("bad-token"))
            .thenThrow(new SystemException(FileErrorCodes.FILE_TOKEN_INVALID));

        SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
        MultipartFile multipart = new MockMultipartFile("file", "test.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> useCase.upload("bad-token", session, multipart, "10.0.0.1"))
            .isInstanceOf(SystemException.class);
        verifyNoInteractions(logRepository);
        verifyNoInteractions(metadataRepository);
    }

    @Test
    @DisplayName("upload verifyAndConsume 失败: 写 FAIL 流水并抛异常")
    void upload_should_write_fail_log_when_verify_fails() {
        FileId fileId = new FileId("01H8TESTFILE002");
        FileMetadata file = newPendingFile(fileId);
        FileTokenPayload payload = new FileTokenPayload(
            "tok-001", fileId, FileUsage.SOURCE, "annuity",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(10)
        );
        when(tokenService.decrypt("encrypted-token")).thenReturn(payload);
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(tokenService.verifyAndConsumeUploadToken(eq("encrypted-token"), any(), eq(file)))
            .thenThrow(new SystemException(FileErrorCodes.FILE_TOKEN_ALREADY_USED));

        SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
        MultipartFile multipart = new MockMultipartFile("file", "test.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> useCase.upload("encrypted-token", session, multipart, "10.0.0.1"))
            .isInstanceOf(SystemException.class);
        ArgumentCaptor<FileAccessLog> logCaptor = ArgumentCaptor.forClass(FileAccessLog.class);
        verify(logRepository).save(logCaptor.capture());
        FileAccessLog savedLog = logCaptor.getValue();
        assertThat(savedLog.result()).isEqualTo(com.example.file.domain.model.aggregate.valueobject.FileAccessResult.FAIL);
        verifyNoInteractions(storageGateway);
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
}
