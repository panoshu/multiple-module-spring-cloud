package com.example.file.application.usecase;

import com.example.file.application.command.ApplyUploadTokenCommand;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ApplyUploadTokenUseCase")
class ApplyUploadTokenUseCaseTest {

    private FileMetadataRepository metadataRepository;
    private StorageTargetResolver targetResolver;
    private FileTokenService tokenService;
    private FileAccessLogRepository logRepository;
    private ApplyUploadTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        targetResolver = mock(StorageTargetResolver.class);
        tokenService = mock(FileTokenService.class);
        logRepository = mock(FileAccessLogRepository.class);
        useCase = new ApplyUploadTokenUseCase(metadataRepository, targetResolver, tokenService, logRepository);
    }

    @Test
    @DisplayName("apply 正常流程: 创建元数据 + 生成 token + 写 APPLY 流水")
    void apply_should_save_metadata_generate_token_and_write_log() {
        StorageTarget target = new StorageTarget(
            "target-001", StorageType.LOCAL, null, null,
            "/data/files", null, null, null, java.util.Map.of()
        );
        when(targetResolver.resolveByUsage(FileUsage.SOURCE, "annuity")).thenReturn(target);
        when(tokenService.generateUploadToken(any(), any(), any(), eq(Duration.ofMinutes(15))))
            .thenReturn("encrypted-token");

        ApplyUploadTokenCommand cmd = new ApplyUploadTokenCommand(
            "annuity", "approval-service", new BatchId("b001"),
            new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
            UserNo.of("u1"), LocalDateTime.now().plusDays(7),
            List.of("application/xlsx"), 10L * 1024 * 1024, Duration.ofMinutes(15)
        );

        ApplyUploadTokenUseCase.ApplyUploadTokenResult result = useCase.apply(cmd);

        assertThat(result.token()).isEqualTo("encrypted-token");
        assertThat(result.fileId()).isNotNull();
        verify(metadataRepository).save(any());
        ArgumentCaptor<FileAccessLog> logCaptor = ArgumentCaptor.forClass(FileAccessLog.class);
        verify(logRepository).save(logCaptor.capture());
        FileAccessLog savedLog = logCaptor.getValue();
        // tokenHash 应为 SHA-256("encrypted-token") 的 64 位十六进制
        assertThat(savedLog.tokenHash()).hasSize(64);
    }

    @Test
    @DisplayName("apply 在 ttl 为空时应抛 IllegalArgumentException")
    void apply_should_throw_when_ttl_is_null() {
        ApplyUploadTokenCommand cmd = new ApplyUploadTokenCommand(
            "annuity", "approval-service", new BatchId("b001"),
            new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
            UserNo.of("u1"), LocalDateTime.now().plusDays(7),
            List.of("application/xlsx"), 10L * 1024 * 1024, null
        );

        assertThatThrownBy(() -> useCase.apply(cmd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ttl 不能为空");
        verifyNoInteractions(metadataRepository, logRepository, tokenService);
    }
}
