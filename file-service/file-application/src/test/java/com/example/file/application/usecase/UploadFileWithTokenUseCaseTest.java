package com.example.file.application.usecase;

import com.example.file.application.service.FileAccessLogWriter;
import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StoreResult;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.*;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
import com.example.shared.exception.SystemException;
import com.example.shared.identifier.id.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("UploadFileWithTokenUseCase")
class UploadFileWithTokenUseCaseTest {

  private FileMetadataRepository metadataRepository;
  private FileTokenService tokenService;
  private FileStorageGateway storageGateway;
  private FileAccessLogRepository logRepository;
  private FileAccessLogWriter fileAccessLogWriter;
  private UploadFileWithTokenUseCase useCase;

  @BeforeEach
  void setUp() {
    metadataRepository = mock(FileMetadataRepository.class);
    tokenService = mock(FileTokenService.class);
    storageGateway = mock(FileStorageGateway.class);
    logRepository = mock(FileAccessLogRepository.class);
    fileAccessLogWriter = mock(FileAccessLogWriter.class);
    useCase = new UploadFileWithTokenUseCase(metadataRepository, tokenService, storageGateway, fileAccessLogWriter);
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

    MultipartFile multipart = new MockMultipartFile("file", "test.xlsx", "application/xlsx", "hello".getBytes());
    SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));

    FileId result = useCase.upload("encrypted-token", session, multipart, "10.0.0.1");

    assertThat(result).isEqualTo(fileId);
    verify(metadataRepository).save(any(FileMetadata.class));
    // SUCCESS 流水通过 FileAccessLogWriter 写入（独立 REQUIRES_NEW 事务）
    verify(fileAccessLogWriter).writeAccessLogSuccess(eq(file), eq(session), eq("10.0.0.1"), eq("encrypted-token"));
    // 不应直接调用 logRepository.save 写 ACCESS 流水
    verifyNoInteractions(logRepository);
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
    verifyNoInteractions(fileAccessLogWriter);
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
    MultipartFile multipart = new MockMultipartFile("file", "test.xlsx", "application/xlsx", "x".getBytes());

    assertThatThrownBy(() -> useCase.upload("encrypted-token", session, multipart, "10.0.0.1"))
      .isInstanceOf(SystemException.class);
    // FAIL 流水通过 FileAccessLogWriter 写入（独立 REQUIRES_NEW 事务）
    verify(fileAccessLogWriter).writeAccessLogFailed(eq(fileId), eq(payload), eq(session), eq("10.0.0.1"), eq("encrypted-token"), anyString());
    verifyNoInteractions(logRepository);
    verifyNoInteractions(storageGateway);
  }

  @Test
  @DisplayName("upload 存储失败: 写 FAIL 流水并包装为 SystemException(FILE_STORAGE_FAILED)")
  void upload_should_write_fail_log_when_store_fails() {
    FileId fileId = new FileId("01H8TESTFILE003");
    FileMetadata file = newPendingFile(fileId);
    FileTokenPayload payload = new FileTokenPayload(
      "tok-001", fileId, FileUsage.SOURCE, "annuity",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(10)
    );
    when(tokenService.decrypt("encrypted-token")).thenReturn(payload);
    when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
    when(storageGateway.store(eq(fileId), any(), eq(5L)))
      .thenThrow(new SystemException(FileErrorCodes.FILE_STORAGE_FAILED));

    SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
    MultipartFile multipart = new MockMultipartFile("file", "test.xlsx", "application/xlsx", "hello".getBytes());

    // SystemException 应透传，不被包装丢失错误码
    assertThatThrownBy(() -> useCase.upload("encrypted-token", session, multipart, "10.0.0.1"))
      .isInstanceOf(SystemException.class)
      .matches(ex -> ((SystemException) ex).code().equals(FileErrorCodes.FILE_STORAGE_FAILED.getCode()));
    verify(fileAccessLogWriter).writeAccessLogFailed(eq(fileId), eq(payload), eq(session), eq("10.0.0.1"), eq("encrypted-token"), anyString());
    verifyNoInteractions(logRepository);
  }

  @Test
  @DisplayName("upload 文件类型不允许: 写 FAIL 流水并抛 SystemException(FILE_CONTENT_TYPE_NOT_ALLOWED)，token 未消费")
  void upload_should_write_fail_log_when_content_type_not_allowed() {
    FileId fileId = new FileId("01H8TESTFILE004");
    FileMetadata file = newPendingFile(fileId);
    FileTokenPayload payload = new FileTokenPayload(
      "tok-001", fileId, FileUsage.SOURCE, "annuity",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(10)
    );
    when(tokenService.decrypt("encrypted-token")).thenReturn(payload);
    when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

    SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
    // contentType 不在 allowedContentTypes 中
    MultipartFile multipart = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

    assertThatThrownBy(() -> useCase.upload("encrypted-token", session, multipart, "10.0.0.1"))
      .isInstanceOf(SystemException.class)
      .matches(ex -> ((SystemException) ex).code().equals(FileErrorCodes.FILE_CONTENT_TYPE_NOT_ALLOWED.getCode()));
    // 校验失败时写 FAIL 流水
    verify(fileAccessLogWriter).writeAccessLogFailed(eq(fileId), eq(payload), eq(session), eq("10.0.0.1"), eq("encrypted-token"), anyString());
    // 校验在 verifyAndConsume 之前，token 不应被消费
    verify(tokenService, never()).verifyAndConsumeUploadToken(anyString(), any(), any());
    verifyNoInteractions(storageGateway);
    verifyNoInteractions(logRepository);
  }

  @Test
  @DisplayName("upload 文件大小超限: 写 FAIL 流水并抛 SystemException(FILE_SIZE_EXCEEDED)，token 未消费")
  void upload_should_write_fail_log_when_size_exceeded() {
    FileId fileId = new FileId("01H8TESTFILE005");
    FileMetadata file = newPendingFile(fileId);
    FileTokenPayload payload = new FileTokenPayload(
      "tok-001", fileId, FileUsage.SOURCE, "annuity",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      null, 1L, LocalDateTime.now().plusMinutes(10)  // 跳过类型校验， maxSize=1
    );
    when(tokenService.decrypt("encrypted-token")).thenReturn(payload);
    when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

    SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
    // size=5 > maxSize=1
    MultipartFile multipart = new MockMultipartFile("file", "test.xlsx", "application/xlsx", "hello".getBytes());

    assertThatThrownBy(() -> useCase.upload("encrypted-token", session, multipart, "10.0.0.1"))
      .isInstanceOf(SystemException.class)
      .matches(ex -> ((SystemException) ex).code().equals(FileErrorCodes.FILE_SIZE_EXCEEDED.getCode()));
    verify(fileAccessLogWriter).writeAccessLogFailed(eq(fileId), eq(payload), eq(session), eq("10.0.0.1"), eq("encrypted-token"), anyString());
    verify(tokenService, never()).verifyAndConsumeUploadToken(anyString(), any(), any());
    verifyNoInteractions(storageGateway);
    verifyNoInteractions(logRepository);
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
