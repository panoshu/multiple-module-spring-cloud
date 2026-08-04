package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.FileId;
import com.example.shared.identifier.id.ProductNo;
import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileTokenPayload 值对象")
class FileTokenPayloadTest {

  @Test
  @DisplayName("上传 token 创建成功")
  void should_create_upload_token() {
    FileTokenPayload payload = new FileTokenPayload(
      "tok-001", new FileId("f001"), FileUsage.SOURCE, "import_declare",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      List.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
      10L * 1024 * 1024, LocalDateTime.now().plusMinutes(15)
    );
    assertThat(payload.tokenId()).isEqualTo("tok-001");
    assertThat(payload.allowedContentTypes()).hasSize(1);
  }

  @Test
  @DisplayName("下载 token allowedContentTypes 可为空")
  void should_create_download_token_with_empty_content_types() {
    FileTokenPayload payload = new FileTokenPayload(
      "tok-002", new FileId("f001"), FileUsage.EXPORT, "export",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      null, null, LocalDateTime.now().plusMinutes(15)
    );
    assertThat(payload.allowedContentTypes()).isNull();
    assertThat(payload.allowedMaxSize()).isNull();
  }

  @Test
  @DisplayName("tokenId 为 null 抛异常")
  void should_throw_when_tokenId_null() {
    assertThatThrownBy(() -> new FileTokenPayload(
      null, new FileId("f001"), FileUsage.SOURCE, "biz",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      null, null, LocalDateTime.now().plusMinutes(15)
    )).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("expireAt 为 null 抛异常")
  void should_throw_when_expireAt_null() {
    assertThatThrownBy(() -> new FileTokenPayload(
      "tok-001", new FileId("f001"), FileUsage.SOURCE, "biz",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      null, null, null
    )).isInstanceOf(IllegalArgumentException.class);
  }
}
