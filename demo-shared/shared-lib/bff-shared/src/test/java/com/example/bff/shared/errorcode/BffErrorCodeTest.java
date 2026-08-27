package com.example.bff.shared.errorcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BffErrorCodeTest {

  @Test
  @DisplayName("所有 BFF 错误码格式合法")
  void allCodes_validFormat() {
    for (BffErrorCode code : BffErrorCode.values()) {
      assertThat(code.getCode()).startsWith("SERVICE.BFF.");
      assertThat(code.getMessage()).isNotBlank();
    }
  }

  @Test
  @DisplayName("错误码全局唯一")
  void allCodes_unique() {
    assertThat(BffErrorCode.values())
      .extracting(BffErrorCode::getCode)
      .doesNotHaveDuplicates();
  }
}
