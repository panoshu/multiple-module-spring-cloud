package com.example.core.domain.business.aggregate.root;

import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.business.aggregate.valueobject.enums.status.FormStatus;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BusinessForm 公开 accessor 验证")
class BusinessFormAccessorTest {

  @Test
  @DisplayName("create() 创建初始状态为 UPLOADED 的表单并注册 FormUploadedEvent")
  void create_setsUploadedStatusAndEvent() {
    BusinessFile file = new BusinessFile(new FileId("F-FILE"), "form.xlsx", "xlsx", 1024L);

    BusinessForm form = BusinessForm.create(
      new FormId("F-1"), BatchId.of("B-1"), buildContext(), buildOperator(), file
    );

    assertThat(form.formStatus()).isEqualTo(FormStatus.UPLOADED);
    assertThat(form.formFile().fileName()).isEqualTo("form.xlsx");
    assertThat(form.batchId().value()).isEqualTo("B-1");
    assertThat(form.businessContext().businessType()).isEqualTo(BusinessType.ACC_PLAN_CREATE);
    assertThat(form.operatorInfo().operatorId().value()).isEqualTo("U-TEST");
    assertThat(form.domainEvents()).hasSize(1);
  }

  @Test
  @DisplayName("reconstitute() 装配所有字段且不触发业务事件")
  void reconstitute_assemblesAllFields() {
    LocalDateTime now = LocalDateTime.now();
    BusinessFile file = new BusinessFile(new FileId("F-FILE"), "form.xlsx", "xlsx", 1024L);

    BusinessForm form = BusinessForm.reconstitute(
      new FormId("F-R"), UserNo.of("U-C"), UserNo.of("U-U"),
      now.minusDays(1), now, Version.of(4L),
      BatchId.of("B-1"), buildContext(), buildOperator(),
      file, FormStatus.PARSED, List.of()
    );

    assertThat(form.id().value()).isEqualTo("F-R");
    assertThat(form.batchId().value()).isEqualTo("B-1");
    assertThat(form.formStatus()).isEqualTo(FormStatus.PARSED);
    assertThat(form.formFile().fileId().value()).isEqualTo("F-FILE");
    assertThat(form.applicationRefs()).isEmpty();
    assertThat(form.version().value()).isEqualTo(4L);
    assertThat(form.domainEvents()).isEmpty();
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
