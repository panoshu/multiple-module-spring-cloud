package com.example.file.domain.service;

import com.example.file.domain.gateway.FileTokenGateway;
import com.example.file.domain.gateway.FileTokenStore;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.*;
import com.example.shared.exception.SystemException;
import com.example.shared.identifier.id.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("FileTokenService 领域服务")
class FileTokenServiceTest {

  private FileTokenGateway tokenGateway;
  private FileTokenStore tokenStore;
  private FileTokenService service;

  @BeforeEach
  void setUp() {
    tokenGateway = mock(FileTokenGateway.class);
    tokenStore = mock(FileTokenStore.class);
    service = new FileTokenService(tokenGateway, tokenStore);
  }

  @Test
  @DisplayName("generateUploadToken 调用 gateway.encrypt 返回密文")
  void should_generate_upload_token() {
    FileMetadata file = newPendingFile();
    when(tokenGateway.encrypt(any())).thenReturn("encrypted-token");

    String token = service.generateUploadToken(file,
      List.of("application/xlsx"), 10L * 1024 * 1024, Duration.ofMinutes(15));

    assertThat(token).isEqualTo("encrypted-token");
    verify(tokenGateway).encrypt(argThat(p -> p.usage() == FileUsage.SOURCE
      && p.allowedContentTypes().contains("application/xlsx")));
  }

  @Test
  @DisplayName("generateDownloadToken 文件未上传抛 SystemException(FILE_NOT_DOWNLOADABLE)")
  void should_throw_when_generate_download_token_for_pending_file() {
    FileMetadata file = newPendingFile();
    assertThatThrownBy(() -> service.generateDownloadToken(file, Duration.ofMinutes(15)))
      .isInstanceOf(SystemException.class)
      .hasMessageContaining("不允许下载");
  }

  @Test
  @DisplayName("verifyAndConsumeUploadToken 成功返回 payload")
  void should_verify_and_consume_upload_token_success() {
    FileMetadata file = newUploadedFile();
    FileTokenPayload payload = new FileTokenPayload(
      "tok-001", file.id(), FileUsage.SOURCE, "biz",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(10)
    );
    when(tokenGateway.decrypt("encrypted-token")).thenReturn(payload);
    when(tokenStore.markUsed(eq("tok-001"), any())).thenReturn(true);

    SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
    FileTokenPayload result = service.verifyAndConsumeUploadToken("encrypted-token", session, file);

    assertThat(result.tokenId()).isEqualTo("tok-001");
    verify(tokenStore).markUsed(eq("tok-001"), any());
  }

  @Test
  @DisplayName("verifyAndConsumeUploadToken 用户不匹配抛异常")
  void should_throw_when_user_mismatch() {
    FileMetadata file = newUploadedFile();
    FileTokenPayload payload = new FileTokenPayload(
      "tok-001", file.id(), FileUsage.SOURCE, "biz",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(10)
    );
    when(tokenGateway.decrypt("encrypted-token")).thenReturn(payload);

    SessionUser session = new SessionUser(UserNo.of("u2"), CustomerNo.of("C001"), ProductNo.of("P001"));
    assertThatThrownBy(() -> service.verifyAndConsumeUploadToken("encrypted-token", session, file))
      .isInstanceOf(SystemException.class);
  }

  @Test
  @DisplayName("verifyAndConsumeUploadToken 企业不匹配抛异常")
  void should_throw_when_customer_mismatch() {
    FileMetadata file = newUploadedFile();
    FileTokenPayload payload = new FileTokenPayload(
      "tok-001", file.id(), FileUsage.SOURCE, "biz",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(10)
    );
    when(tokenGateway.decrypt("encrypted-token")).thenReturn(payload);

    SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C002"), ProductNo.of("P001"));
    assertThatThrownBy(() -> service.verifyAndConsumeUploadToken("encrypted-token", session, file))
      .isInstanceOf(SystemException.class);
  }

  @Test
  @DisplayName("verifyAndConsumeUploadToken token 已使用抛异常")
  void should_throw_when_token_already_used() {
    FileMetadata file = newUploadedFile();
    FileTokenPayload payload = new FileTokenPayload(
      "tok-001", file.id(), FileUsage.SOURCE, "biz",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(10)
    );
    when(tokenGateway.decrypt("encrypted-token")).thenReturn(payload);
    when(tokenStore.markUsed(eq("tok-001"), any())).thenReturn(false);

    SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
    assertThatThrownBy(() -> service.verifyAndConsumeUploadToken("encrypted-token", session, file))
      .isInstanceOf(SystemException.class);
  }

  @Test
  @DisplayName("verifyAndConsumeUploadToken 过期抛异常")
  void should_throw_when_token_expired() {
    FileMetadata file = newUploadedFile();
    FileTokenPayload payload = new FileTokenPayload(
      "tok-001", file.id(), FileUsage.SOURCE, "biz",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().minusMinutes(1)
    );
    when(tokenGateway.decrypt("encrypted-token")).thenReturn(payload);

    SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
    assertThatThrownBy(() -> service.verifyAndConsumeUploadToken("encrypted-token", session, file))
      .isInstanceOf(SystemException.class);
  }

  @Test
  @DisplayName("verifyAndConsumeDownloadToken 成功返回 payload")
  void should_verify_and_consume_download_token_success() {
    FileMetadata file = newUploadedFile();
    FileTokenPayload payload = new FileTokenPayload(
      "tok-002", file.id(), FileUsage.EXPORT, "biz",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      null, null, LocalDateTime.now().plusMinutes(10)
    );
    when(tokenGateway.decrypt("encrypted-token")).thenReturn(payload);
    when(tokenStore.markUsed(eq("tok-002"), any())).thenReturn(true);

    SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
    FileTokenPayload result = service.verifyAndConsumeDownloadToken("encrypted-token", session, file);

    assertThat(result.tokenId()).isEqualTo("tok-002");
  }

  private FileMetadata newPendingFile() {
    return FileMetadata.createForUpload(
      new FileId("f001"), FileUsage.SOURCE, "biz", "approval-service",
      new BatchId("b001"),
      new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
      "target-001", StorageType.LOCAL, UserNo.of("u1"),
      LocalDateTime.now().plusDays(7)
    );
  }

  private FileMetadata newUploadedFile() {
    FileMetadata file = newPendingFile();
    file.completeUpload("report.xlsx", 1024L, "application/xlsx", "storage-key", "sm3-digest");
    return file;
  }
}
