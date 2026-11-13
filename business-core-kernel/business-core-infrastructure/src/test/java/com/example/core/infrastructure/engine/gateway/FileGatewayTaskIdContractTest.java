package com.example.core.infrastructure.engine.gateway;

import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.types.FormId;
import com.example.file.api.FileAccessApi;
import com.example.file.api.FileTaskApi;
import com.example.file.api.OutboundApi;
import com.example.file.api.ParsedDataApi;
import com.example.file.api.response.FileTaskIdResponse;
import com.example.file.api.response.OutboundFileIdResponse;
import com.example.file.types.FileId;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** FILE_* 网关 externalTaskId 契约（阶段 B 派发前置，批次 27）。 */
@DisplayName("FILE_* 网关 externalTaskId 返回契约")
class FileGatewayTaskIdContractTest {

  private static final FormId FORM_ID = FormId.of("FORM-1");
  private static final FileId FILE_ID = new FileId("FILE-1");
  private static final String REQUEST_NO = "SE-9";

  @Test
  @DisplayName("SPI 声明两个带返回值的 default 重载")
  void spiDeclaresTaskIdOverloads() throws NoSuchMethodException {
    assertThat(FileIntegrationGateway.class.getMethod("triggerAsyncParsingAndGetTaskId",
      FormId.class, FileId.class, String.class, Map.class, String.class)).isNotNull();
    assertThat(FileIntegrationGateway.class.getMethod("triggerOutboundGenerationAndGetTaskId",
      String.class, String.class, Map.class, String.class, String.class)).isNotNull();
  }

  @Test
  @DisplayName("default 重载委托旧签名并返回 null（annuity Mock 不重写时兼容）")
  void defaultOverloadsReturnNull() {
    FileIntegrationGateway stub = new LegacyOnlyGateway();

    assertThat(stub.triggerAsyncParsingAndGetTaskId(FORM_ID, FILE_ID, "TPL",
      Map.of(), REQUEST_NO)).isNull();
    assertThat(stub.triggerOutboundGenerationAndGetTaskId("FT", "TPL",
      Map.of(), "SYSTEM", REQUEST_NO)).isNull();
    assertThat(((LegacyOnlyGateway) stub).parsingCalled).isTrue();
    assertThat(((LegacyOnlyGateway) stub).outboundCalled).isTrue();
  }

  @Test
  @DisplayName("kernel 实现覆盖解析重载并返回真实 fileTaskId")
  void kernelParsingReturnsTaskId() {
    FileTaskApi fileTaskApi = mock(FileTaskApi.class);
    when(fileTaskApi.upload(any())).thenReturn(ApiResult.success(new FileTaskIdResponse("TASK-77")));
    FileServiceIntegrationGateway gateway = gateway(fileTaskApi, mock(OutboundApi.class));

    String taskId = gateway.triggerAsyncParsingAndGetTaskId(
      FORM_ID, FILE_ID, "TPL", Map.of(), REQUEST_NO);

    assertThat(taskId).isEqualTo("TASK-77");
  }

  @Test
  @DisplayName("kernel 实现覆盖出站重载并返回真实 fileTaskId")
  void kernelOutboundReturnsTaskId() {
    OutboundApi outboundApi = mock(OutboundApi.class);
    when(outboundApi.generate(any())).thenReturn(
      ApiResult.success(new OutboundFileIdResponse("TASK-88", 2)));
    FileServiceIntegrationGateway gateway = gateway(mock(FileTaskApi.class), outboundApi);

    String taskId = gateway.triggerOutboundGenerationAndGetTaskId(
      "FT", "TPL", Map.of(), "SYSTEM", REQUEST_NO);

    assertThat(taskId).isEqualTo("TASK-88");
  }

  @Test
  @DisplayName("派发失败（result 不成功）返回 null，不抛异常")
  void kernelReturnsNullOnFailure() {
    FileTaskApi fileTaskApi = mock(FileTaskApi.class);
    when(fileTaskApi.upload(any())).thenReturn(null);
    FileServiceIntegrationGateway gateway = gateway(fileTaskApi, mock(OutboundApi.class));

    assertThat(gateway.triggerAsyncParsingAndGetTaskId(
      FORM_ID, FILE_ID, "TPL", Map.of(), REQUEST_NO)).isNull();
  }

  private FileServiceIntegrationGateway gateway(FileTaskApi fileTaskApi, OutboundApi outboundApi) {
    return new FileServiceIntegrationGateway(
      mock(FileAccessApi.class), mock(ParsedDataApi.class), fileTaskApi, outboundApi);
  }

  /** 只实现 abstract 方法，不覆盖新增 default —— 模拟 annuity Mock 的行为。 */
  private static final class LegacyOnlyGateway implements FileIntegrationGateway {
    private boolean parsingCalled;
    private boolean outboundCalled;

    @Override
    public void triggerAsyncParsing(BusinessForm form, BusinessMetaContext context) {
    }

    @Override
    public void triggerAsyncParsing(FormId formId, FileId sourceFileId, String parseTemplateId,
                                    Map<String, Object> splitRules) {
      parsingCalled = true;
    }

    @Override
    public InputStream downloadStream(FileId fileId, BusinessMetaContext context) {
      return null;
    }

    @Override
    public String applyUploadToken(String clientIp, String userId, long maxSize) {
      return null;
    }

    @Override
    public void triggerOutboundGeneration(String fileTaskId, String targetTemplateId,
                                           Map<String, Object> supplementalData, String operator) {
      outboundCalled = true;
    }

    @Override
    public ParsedRowsPage fetchParsedRows(String subTaskId, String tableCode, int page, int size) {
      return null;
    }
  }
}
