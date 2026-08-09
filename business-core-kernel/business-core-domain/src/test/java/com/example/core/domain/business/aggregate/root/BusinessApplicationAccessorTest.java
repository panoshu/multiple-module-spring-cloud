package com.example.core.domain.business.aggregate.root;

import com.example.core.domain.business.aggregate.valueobject.*;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.business.aggregate.valueobject.enums.status.ApplicationStatus;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BusinessApplication 公开 accessor 验证")
class BusinessApplicationAccessorTest {

  @Test
  @DisplayName("businessExtension() 返回扩展字段实例")
  void businessExtension_returnsExtensionInstance() {
    BusinessApplication app = buildTestApp();
    assertThat(app.businessExtension()).isNull();
  }

  @Test
  @DisplayName("operatorInfo() 返回操作人信息")
  void operatorInfo_returnsOperatorInfo() {
    BusinessApplication app = buildTestApp();
    assertThat(app.operatorInfo()).isNotNull();
    assertThat(app.operatorInfo().operatorId().value()).isEqualTo("U-TEST");
  }

  @Test
  @DisplayName("businessContext() 返回业务上下文")
  void businessContext_returnsBusinessContext() {
    BusinessApplication app = buildTestApp();
    assertThat(app.businessContext()).isNotNull();
    assertThat(app.businessContext().businessType()).isEqualTo(BusinessType.ACC_PLAN_CREATE);
  }

  @Test
  @DisplayName("attachExtension() 挂载业务扩展字段")
  void attachExtension_setsExtension() {
    BusinessApplication app = buildTestApp();
    BusinessExtension stub = new BusinessExtension() {
      @Override
      public BusinessType businessType() {
        return BusinessType.ACC_PLAN_CREATE;
      }
    };
    app.attachExtension(stub);
    assertThat(app.businessExtension()).isSameAs(stub);
  }

  @Test
  @DisplayName("reconstitute() 装配所有字段且不触发业务事件")
  void reconstitute_assemblesAllFields() {
    LocalDateTime now = LocalDateTime.now();
    MaterialItem item = new MaterialItem("M1", "材料1", null, null, null, Optional.empty());

    BusinessApplication app = BusinessApplication.reconstitute(
      new ApplicationId("APP-R"), UserNo.of("U-C"), UserNo.of("U-U"),
      now.minusDays(1), now, Version.of(3L),
      BatchId.of("B-1"), new FormId("F-1"),
      buildContext(), buildOperator(), null,
      new FileId("FILE-R"), 5,
      ApplicationStatus.COMPLETED, ApplicationFlowStep.COMPLETED,
      now, now,
      List.of(item), new BusinessFile(new FileId("PKG-1"), "pkg.zip", "zip", 100L)
    );

    assertThat(app.id().value()).isEqualTo("APP-R");
    assertThat(app.batchId().value()).isEqualTo("B-1");
    assertThat(app.formId().value()).isEqualTo("F-1");
    assertThat(app.parsedJsonFileId().value()).isEqualTo("FILE-R");
    assertThat(app.expectedDetailCount()).isEqualTo(5);
    assertThat(app.status()).isEqualTo(ApplicationStatus.COMPLETED);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.COMPLETED);
    assertThat(app.applyTime()).isEqualTo(now);
    assertThat(app.completeTime()).isEqualTo(now);
    assertThat(app.packageFile().fileName()).isEqualTo("pkg.zip");
    assertThat(app.planMaterials()).hasSize(1);
    assertThat(app.version().value()).isEqualTo(3L);
    assertThat(app.domainEvents()).isEmpty();
  }

  private BusinessApplication buildTestApp() {
    return BusinessApplication.createFromForm(
      new ApplicationId("APP-001"), buildContext(), buildOperator(), new FileId("FILE-001")
    );
  }

  private BusinessContext buildContext() {
    return new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("C-001"), "客户",
      ProductNo.of("P-001"), "产品",
      PlanNo.of("PL-001"), "方案",
      OperationModel.Single_Trustee, AccountManager.CJP
    );
  }

  private OperatorInfo buildOperator() {
    return new OperatorInfo(
      AnnuityChannel.NETAPP, UserNo.of("U-TEST"), "操作人", false
    );
  }
}
