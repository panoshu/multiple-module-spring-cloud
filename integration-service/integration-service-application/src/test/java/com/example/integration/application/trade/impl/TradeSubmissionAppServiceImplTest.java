package com.example.integration.application.trade.impl;

import com.example.integration.domain.trade.gateway.FileSubmissionGateway;
import com.example.integration.domain.trade.gateway.OutboundSubmissionReceiptGateway;
import com.example.integration.domain.trade.model.FileSubmissionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("I-02 出站提交幂等")
class TradeSubmissionAppServiceImplTest {

  private FileSubmissionGateway fileSubmissionGateway;
  private OutboundSubmissionReceiptGateway receiptGateway;
  private TradeSubmissionAppServiceImpl service;

  @BeforeEach
  void setUp() {
    fileSubmissionGateway = mock(FileSubmissionGateway.class);
    receiptGateway = mock(OutboundSubmissionReceiptGateway.class);
    service = new TradeSubmissionAppServiceImpl(fileSubmissionGateway, receiptGateway);
  }

  @Test
  @DisplayName("首次提交成功后写 SUCCESS 回执")
  void firstSubmissionMarksSuccess() {
    when(receiptGateway.tryAcquire("E-1", "F-1")).thenReturn(true);
    when(fileSubmissionGateway.submit("F-1", "5565", Map.of()))
      .thenReturn(FileSubmissionResult.success("5565", "S-1"));

    FileSubmissionResult result = service.submitOutboundFileOnce(
      "E-1", "F-1", "5565", Map.of());

    assertThat(result.status()).isEqualTo(FileSubmissionResult.SubmissionStatus.SUCCESS);
    verify(receiptGateway).markSuccess("E-1", "F-1");
  }

  @Test
  @DisplayName("相同事件/文件重复投递直接返回 DUPLICATE，不再调用外部系统")
  void duplicateEventIsSkipped() {
    when(receiptGateway.tryAcquire("E-1", "F-1")).thenReturn(false);

    FileSubmissionResult result = service.submitOutboundFileOnce(
      "E-1", "F-1", "5565", Map.of());

    assertThat(result.status()).isEqualTo(FileSubmissionResult.SubmissionStatus.DUPLICATE);
    verify(fileSubmissionGateway, never()).submit(any(), any(), anyMap());
  }

  @Test
  @DisplayName("提交失败释放回执，允许 MQ 重投重试")
  void failureReleasesReceipt() {
    when(receiptGateway.tryAcquire("E-1", "F-1")).thenReturn(true);
    when(fileSubmissionGateway.submit("F-1", "5565", Map.of()))
      .thenThrow(new IllegalStateException("network"));

    assertThatThrownBy(() -> service.submitOutboundFileOnce(
      "E-1", "F-1", "5565", Map.of()))
      .isInstanceOf(IllegalStateException.class);
    verify(receiptGateway).release("E-1", "F-1");
  }
}
