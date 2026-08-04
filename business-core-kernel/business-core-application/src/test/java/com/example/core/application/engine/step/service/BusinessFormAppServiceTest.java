package com.example.core.application.engine.step.service;

import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.business.repository.FormRepository;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.shared.domain.event.EventBus;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.FormId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * BusinessFormAppService 单元测试
 *
 * <p>覆盖 Task 6 新增的 applyUploadToken / deleteForm / getFormStatus 方法。
 * confirmUpload / triggerParsing / handleParsingResult 已有方法不在本测试范围。
 *
 * @author panoshu
 */
class BusinessFormAppServiceTest {

  private FormRepository formRepository;
  private ApplicationRepository applicationRepository;
  private BusinessConfigGateway configGateway;
  private FileIntegrationGateway fileIntegrationGateway;
  private EventBus eventBus;
  private IdService idService;
  private BusinessFormAppService appService;

  @BeforeEach
  void setUp() {
    formRepository = mock(FormRepository.class);
    applicationRepository = mock(ApplicationRepository.class);
    configGateway = mock(BusinessConfigGateway.class);
    fileIntegrationGateway = mock(FileIntegrationGateway.class);
    eventBus = mock(EventBus.class);
    idService = mock(IdService.class);
    appService = new BusinessFormAppService(
      formRepository, applicationRepository, configGateway,
      fileIntegrationGateway, eventBus, idService
    );
  }

  @Test
  void should_apply_upload_token_via_gateway() {
    String clientIp = "127.0.0.1";
    String userId = "U001";
    long fileSize = 1024L;
    when(fileIntegrationGateway.applyUploadToken(clientIp, userId, fileSize))
      .thenReturn("token-abc-123");

    String token = appService.applyUploadToken(clientIp, userId, fileSize);

    assertThat(token).isEqualTo("token-abc-123");
    verify(fileIntegrationGateway).applyUploadToken(clientIp, userId, fileSize);
  }

  @Test
  void should_delete_form_and_save() {
    FormId formId = new FormId("FORM001");
    BusinessForm form = mock(BusinessForm.class);
    when(formRepository.loadOrThrow(formId)).thenReturn(form);
    when(form.domainEvents()).thenReturn(java.util.List.of());

    appService.deleteForm(formId);

    verify(form).markAsDeleted();
    verify(formRepository).save(form);
  }

  @Test
  void should_get_form_status() {
    FormId formId = new FormId("FORM001");
    BusinessForm form = mock(BusinessForm.class);
    when(formRepository.loadOrThrow(formId)).thenReturn(form);

    BusinessForm result = appService.getFormStatus(formId);

    assertThat(result).isSameAs(form);
    verify(formRepository).loadOrThrow(formId);
  }
}
