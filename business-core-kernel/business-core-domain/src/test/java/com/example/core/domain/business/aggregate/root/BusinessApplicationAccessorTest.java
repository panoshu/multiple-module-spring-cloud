package com.example.core.domain.business.aggregate.root;

import com.example.core.domain.business.aggregate.valueobject.*;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.types.Channel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.types.OperatingMode;
import com.example.core.domain.business.aggregate.valueobject.enums.status.ApplicationStatus;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.core.types.ApplicationId;
import com.example.core.types.BatchId;
import com.example.core.types.FormId;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.core.types.CustomerNo;
import com.example.file.types.FileId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    assertThat(app.operatorInfo().operatorId()).isEqualTo("U-TEST");
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
      new ApplicationId("APP-R"), "U-C", "U-U",
      now.minusDays(1), now, Version.of(3L),
      BatchId.of("B-1"), new FormId("F-1"),
      buildContext(), buildOperator(), null,
      new FileId("FILE-R"), 5,
      DefinitionVersion.of("v2"),
      ApplicationStatus.COMPLETED, ApplicationFlowStep.COMPLETED,
      now, now,
      List.of(item), new BusinessFile(new FileId("PKG-1"), "pkg.zip", "zip", 100L)
    );

    assertThat(app.id().value()).isEqualTo("APP-R");
    assertThat(app.batchId().value()).isEqualTo("B-1");
    assertThat(app.formId().value()).isEqualTo("F-1");
    assertThat(app.parsedJsonFileId().value()).isEqualTo("FILE-R");
    assertThat(app.expectedDetailCount()).isEqualTo(5);
    assertThat(app.definitionVersion()).isEqualTo(DefinitionVersion.of("v2"));
    assertThat(app.status()).isEqualTo(ApplicationStatus.COMPLETED);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.COMPLETED);
    assertThat(app.applyTime()).isEqualTo(now);
    assertThat(app.completeTime()).isEqualTo(now);
    assertThat(app.packageFile().fileName()).isEqualTo("pkg.zip");
    assertThat(app.planMaterials()).hasSize(1);
    assertThat(app.version().value()).isEqualTo(3L);
    assertThat(app.domainEvents()).isEmpty();
  }

  @Test
  @DisplayName("createFromForm() 保存表单、批次归属与定义版本")
  void createFromForm_savesSourceReferences() {
    BusinessApplication app = buildTestApp();

    assertThat(app.formId().value()).isEqualTo("F-001");
    assertThat(app.batchId().value()).isEqualTo("B-001");
    assertThat(app.definitionVersion()).isEqualTo(DefinitionVersion.initial());
    assertThat(app.status()).isEqualTo(ApplicationStatus.RUNNING);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.FORM_DETAIL_INGESTION);
    assertThat(app.domainEvents()).hasSize(1);
  }

  @Test
  @DisplayName("申请来源必须包含表单")
  void applicationSource_requiresFormId() {
    assertThatThrownBy(() -> new ApplicationSource(null, BatchId.of("B-001")))
      .isInstanceOf(DomainException.class);
  }

  private BusinessApplication buildTestApp() {
    return BusinessApplication.createFromForm(
      new ApplicationId("APP-001"),
      new ApplicationSource(new FormId("F-001"), BatchId.of("B-001")),
      buildContext(), buildOperator(), new FileId("FILE-001"), DefinitionVersion.initial()
    );
  }

  private BusinessContext buildContext() {
    return new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("C-001"), "客户",
      ProductNo.of("P-001"), "产品",
      PlanNo.of("PL-001"), "方案",
      OperatingMode.TRUSTEE_ONLY, AccountManager.CJP
    );
  }

  private OperatorInfo buildOperator() {
    return new OperatorInfo(
      Channel.NETAPP, "U-TEST", "操作人", false
    );
  }
}
