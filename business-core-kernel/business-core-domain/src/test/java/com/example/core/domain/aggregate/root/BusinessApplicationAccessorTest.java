package com.example.core.domain.aggregate.root;

import com.example.core.domain.aggregate.valueobject.BusinessContext;
import com.example.core.domain.aggregate.valueobject.BusinessExtension;
import com.example.core.domain.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.aggregate.valueobject.business.OperationModel;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

  private BusinessApplication buildTestApp() {
    BusinessContext context = new BusinessContext(
        BusinessType.ACC_PLAN_CREATE,
        CustomerNo.of("C-001"), "客户",
        ProductNo.of("P-001"), "产品",
        PlanNo.of("PL-001"), "方案",
        OperationModel.Single_Trustee, AccountManager.CJP
    );
    OperatorInfo operator = new OperatorInfo(
        AnnuityChannel.NETAPP, UserNo.of("U-TEST"), "操作人", false
    );
    return BusinessApplication.createFromForm(
        new ApplicationId("APP-001"), context, operator, new FileId("FILE-001")
    );
  }
}
