package com.example.core.application.engine.listener;

import com.example.core.application.engine.step.service.BusinessFormAppService;
import com.example.core.domain.business.event.FormUploadedEvent;
import com.example.core.types.FormId;
import com.example.core.types.FileRefId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("FormUploadedEventListener 表单上传事件监听")
class FormUploadedEventListenerTest {

  private final BusinessFormAppService formAppService = mock(BusinessFormAppService.class);
  private final FormUploadedEventListener listener = new FormUploadedEventListener(formAppService);

  @Test
  @DisplayName("收到事件后触发异步解析")
  void onFormUploaded_triggersParsing() {
    FormId formId = new FormId("F-1");
    FormUploadedEvent event = FormUploadedEvent.of(
      formId, new FileRefId("FILE-1"), "form.xlsx");

    listener.onFormUploaded(event);

    verify(formAppService).triggerParsing(formId);
  }

  @Test
  @DisplayName("解析触发失败不反向影响已提交的上传事务")
  void onFormUploaded_isolatesFailure() {
    FormId formId = new FormId("F-2");
    FormUploadedEvent event = FormUploadedEvent.of(
      formId, new FileRefId("FILE-2"), "form.xlsx");
    doThrow(new IllegalStateException("file-service unavailable"))
      .when(formAppService).triggerParsing(formId);

    listener.onFormUploaded(event);

    verify(formAppService).triggerParsing(formId);
  }
}
