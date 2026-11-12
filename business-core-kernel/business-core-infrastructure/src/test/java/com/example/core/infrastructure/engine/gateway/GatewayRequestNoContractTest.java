package com.example.core.infrastructure.engine.gateway;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.types.FormId;
import com.example.file.types.FileId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("外部协作 Gateway 实现 requestNo 重载契约")
class GatewayRequestNoContractTest {

  @Test
  @DisplayName("两个 Gateway 实现均覆盖带 requestNo 的重载（不依赖默认委托）")
  void implementationsOverrideRequestNoOverloads() throws NoSuchMethodException {
    assertThat(ApprovalServiceIntegrationGateway.class.getDeclaredMethod(
      "startApproval", BusinessApplication.class, String.class)).isNotNull();
    assertThat(FileServiceIntegrationGateway.class.getDeclaredMethod(
      "triggerAsyncParsing", FormId.class, FileId.class, String.class, Map.class, String.class)).isNotNull();
    assertThat(FileServiceIntegrationGateway.class.getDeclaredMethod(
      "triggerOutboundGeneration", String.class, String.class, Map.class, String.class, String.class)).isNotNull();
  }

  @Test
  @DisplayName("旧签名仍保留，业务表单与既有调用零改动")
  void legacySignaturesRemainAvailable() throws NoSuchMethodException {
    assertThat(ApprovalServiceIntegrationGateway.class.getDeclaredMethod(
      "startApproval", BusinessApplication.class)).isNotNull();
    assertThat(FileServiceIntegrationGateway.class.getDeclaredMethod(
      "triggerAsyncParsing", FormId.class, FileId.class, String.class, Map.class)).isNotNull();
    assertThat(FileServiceIntegrationGateway.class.getDeclaredMethod(
      "triggerOutboundGeneration", String.class, String.class, Map.class, String.class)).isNotNull();
  }
}
