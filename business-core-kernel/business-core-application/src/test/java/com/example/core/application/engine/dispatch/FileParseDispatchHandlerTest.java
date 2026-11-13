package com.example.core.application.engine.dispatch;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.config.FormParsingConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.FormId;
import com.example.core.types.StepExecutionId;
import com.example.file.types.FileId;
import com.example.shared.domain.aggregate.valueobject.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FILE_PARSE 派发处理器（阶段 B 事务外参数重建，批次 29）。 */
@DisplayName("FileParseDispatchHandler 事务外参数重建与派发")
class FileParseDispatchHandlerTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);
  private static final ApplicationId APP_ID = new ApplicationId("APP-1");
  private static final FormId FORM_ID = FormId.of("FORM-1");
  private static final FileId FILE_ID = new FileId("FILE-1");

  private ApplicationRepository applicationRepository;
  private BusinessConfigGateway configGateway;
  private FileIntegrationGateway fileGateway;
  private FileParseDispatchHandler handler;

  @BeforeEach
  void setUp() {
    applicationRepository = mock(ApplicationRepository.class);
    configGateway = mock(BusinessConfigGateway.class);
    fileGateway = mock(FileIntegrationGateway.class);
    handler = new FileParseDispatchHandler(applicationRepository, configGateway, fileGateway);
  }

  @Test
  @DisplayName("supportedType 为 FILE_PARSE")
  void supportedType_isFileParse() {
    assertThat(handler.supportedType()).isEqualTo(InteractionType.FILE_PARSE);
  }

  @Test
  @DisplayName("从 app + configGateway 重建参数，返回网关的 externalTaskId")
  void dispatch_rebuildsParamsAndReturnsTaskId() {
    BusinessApplication app = mock(BusinessApplication.class);
    when(app.buildConfigQueryContext()).thenReturn(mock(BusinessMetaContext.class));
    when(app.formId()).thenReturn(FORM_ID);
    when(app.requireFileForParsing()).thenReturn(FILE_ID);
    when(applicationRepository.loadOrThrow(APP_ID)).thenReturn(app);
    when(configGateway.getFormParsingConfig(any()))
      .thenReturn(new FormParsingConfig("TPL-1", false, Map.of("by", "dept")));
    when(fileGateway.triggerAsyncParsingAndGetTaskId(
      FORM_ID, FILE_ID, "TPL-1", Map.of("by", "dept"), "SE-9")).thenReturn("TASK-9");
    ExternalInteraction interaction = interaction();

    String taskId = handler.dispatch(interaction);

    assertThat(taskId).isEqualTo("TASK-9");
    verify(fileGateway).triggerAsyncParsingAndGetTaskId(
      FORM_ID, FILE_ID, "TPL-1", Map.of("by", "dept"), "SE-9");
  }

  @Test
  @DisplayName("解析配置缺失时返回 null，不调网关（保持 DISPATCHING 待对账）")
  void dispatch_returnsNullWhenConfigMissing() {
    BusinessApplication app = mock(BusinessApplication.class);
    when(app.buildConfigQueryContext()).thenReturn(mock(BusinessMetaContext.class));
    when(applicationRepository.loadOrThrow(APP_ID)).thenReturn(app);
    when(configGateway.getFormParsingConfig(any())).thenReturn(null);
    ExternalInteraction interaction = interaction();

    assertThat(handler.dispatch(interaction)).isNull();

    verify(fileGateway, never()).triggerAsyncParsingAndGetTaskId(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("parseTemplateId 为空时返回 null，不调网关")
  void dispatch_returnsNullWhenTemplateBlank() {
    BusinessApplication app = mock(BusinessApplication.class);
    when(app.buildConfigQueryContext()).thenReturn(mock(BusinessMetaContext.class));
    when(applicationRepository.loadOrThrow(APP_ID)).thenReturn(app);
    when(configGateway.getFormParsingConfig(any()))
      .thenReturn(new FormParsingConfig("  ", false, Map.of()));
    ExternalInteraction interaction = interaction();

    assertThat(handler.dispatch(interaction)).isNull();

    verify(fileGateway, never()).triggerAsyncParsingAndGetTaskId(any(), any(), any(), any(), any());
  }

  private ExternalInteraction interaction() {
    return ExternalInteraction.reconstitute(
      ExternalInteractionId.of(1L), "U-C", "U-U", NOW.minusDays(1), NOW, Version.of(1L),
      APP_ID, StepExecutionId.of(9L), InteractionType.FILE_PARSE,
      "SE-9", null, ExternalInteractionStatus.DISPATCHING,
      null, 0, null, null, null);
  }
}
