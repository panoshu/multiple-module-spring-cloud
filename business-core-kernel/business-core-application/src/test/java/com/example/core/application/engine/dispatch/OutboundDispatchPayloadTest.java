package com.example.core.application.engine.dispatch;

import com.example.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FILE_OUTBOUND 派发载荷的紧凑编解码（批次 30）。 */
@DisplayName("OutboundDispatchPayload 编解码")
class OutboundDispatchPayloadTest {

  @Test
  @DisplayName("编解码往返保留两个凭证")
  void encodeDecode_roundTrip() {
    OutboundDispatchPayload payload = new OutboundDispatchPayload("FT-1", "TPL-1");

    OutboundDispatchPayload decoded = OutboundDispatchPayload.decode(payload.encode());

    assertThat(decoded.fileTaskId()).isEqualTo("FT-1");
    assertThat(decoded.targetTemplateId()).isEqualTo("TPL-1");
  }

  @Test
  @DisplayName("编码结果紧凑且不含分隔符之外的空白")
  void encode_isCompact() {
    String encoded = new OutboundDispatchPayload("FT-1", "TPL-1").encode();

    assertThat(encoded).isEqualTo("FT-1\u001FTPL-1");
    assertThat(encoded.length()).isLessThan(255);
  }

  @Test
  @DisplayName("空载荷/格式错误拒绝解码")
  void decode_rejectsMalformed() {
    assertThatThrownBy(() -> OutboundDispatchPayload.decode(null))
      .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> OutboundDispatchPayload.decode("only-one-part"))
      .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> OutboundDispatchPayload.decode("\u001FTPL-1"))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("凭证含分隔符时拒绝编码，避免串字段")
  void encode_rejectsSeparatorInFields() {
    assertThatThrownBy(() -> new OutboundDispatchPayload("FT\u001F1", "TPL-1").encode())
      .isInstanceOf(DomainException.class);
  }
}
