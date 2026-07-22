package com.example.annuity.application.handler;

import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.domain.service.AnnuityEmployeeVerificationRule;
import com.example.annuity.domain.service.AnnuityExtensionResolver;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.shared.primitives.identity.ApplicationId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnnuityDataVerificationHandler 编排逻辑")
class AnnuityDataVerificationHandlerTest {

  @Mock private AnnuityExtensionResolver extensionResolver;
  @Mock private AnnuityEmployeeVerificationRule verificationRule;
  @Mock private AnnuityEmployeeBatchRepository batchRepository;
  @InjectMocks private AnnuityDataVerificationHandler handler;

  @Test
  @DisplayName("所有明细核查通过 - 返回 SUCCESS 且批次完成")
  void execute_allVerifiedReturnsSuccess() {
    ApplicationId appId = new ApplicationId("APP-001");
    BusinessApplication app = mock(BusinessApplication.class);
    when(app.id()).thenReturn(appId);
    AnnuityEmployeeBatch batch = mock(AnnuityEmployeeBatch.class);
    when(batch.pendingDetails()).thenReturn(List.of());
    when(batch.isAllProcessed()).thenReturn(true);
    when(batchRepository.findByApplicationId(appId)).thenReturn(Optional.of(batch));

    StepExecutionStatus status = handler.execute(app, null);

    assertThat(status).isEqualTo(StepExecutionStatus.SUCCESS);
    verify(batch).complete();
    verify(batchRepository).save(batch);
  }

  @Test
  @DisplayName("handlerName 返回固定标识")
  void handlerName_returnsFixedIdentifier() {
    assertThat(handler.handlerName()).isEqualTo("annuityDataVerificationHandler");
  }
}
