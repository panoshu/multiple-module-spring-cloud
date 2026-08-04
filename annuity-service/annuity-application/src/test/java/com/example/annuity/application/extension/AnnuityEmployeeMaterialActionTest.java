package com.example.annuity.application.extension;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.domain.service.AnnuityEmployeeMaterialRule;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.UserNo;
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
@DisplayName("AnnuityEmployeeMaterialAction")
class AnnuityEmployeeMaterialActionTest {

  @Mock
  private AnnuityEmployeeMaterialRule materialRule;
  @Mock
  private AnnuityEmployeeBatchRepository batchRepository;
  @InjectMocks
  private AnnuityEmployeeMaterialAction action;

  @Test
  @DisplayName("为已核查明细计算材料清单")
  void execute_calculatesMaterialsForVerifiedDetails() {
    ApplicationId appId = new ApplicationId("APP-001");
    BusinessApplication app = mock(BusinessApplication.class);
    when(app.id()).thenReturn(appId);

    AnnuityEmployeeDetail detail = mock(AnnuityEmployeeDetail.class);
    AnnuityEmployeeBatch batch = mock(AnnuityEmployeeBatch.class);
    when(batch.verifiedDetails()).thenReturn(List.of(detail));
    when(batchRepository.findByApplicationId(appId)).thenReturn(Optional.of(batch));
    when(materialRule.calculate(detail, null)).thenReturn(List.of(
      new AnnuityEmployeeMaterial("ID_CARD", "身份证", true, false, null)
    ));
    when(app.updatedBy()).thenReturn(UserNo.of("U-TEST"));

    ExtensionExecutionResult result = action.execute(app, null, java.util.Map.of());

    assertThat(result.isSuccess()).isTrue();
    verify(detail).assignMaterials(anyList(), eq(UserNo.of("U-TEST")));
    verify(batchRepository).save(batch);
  }
}
