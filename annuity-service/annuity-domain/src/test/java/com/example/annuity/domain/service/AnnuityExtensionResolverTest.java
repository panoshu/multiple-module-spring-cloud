package com.example.annuity.domain.service;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessContext;
import com.example.core.domain.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.aggregate.valueobject.business.OperationModel;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AnnuityExtensionResolver 扩展字段解析器")
class AnnuityExtensionResolverTest {

  private final AnnuityExtensionResolver resolver = new AnnuityExtensionResolver();

  @Test
  @DisplayName("扩展字段为 null 时抛出 DomainException")
  void resolve_nullExtensionThrows() {
    BusinessApplication app = createApp();
    assertThatThrownBy(() -> resolver.resolve(app))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("扩展字段类型匹配时返回强类型实例")
  void resolve_correctTypeReturns() throws Exception {
    BusinessApplication app = createApp();
    // 通过反射设置扩展字段(模拟 Jackson 反序列化后的状态)
    var field = BusinessApplication.class.getDeclaredField("businessExtension");
    field.setAccessible(true);
    field.set(app, new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, false
    ));

    AnnuityApplicationExtension ext = resolver.resolve(app);
    assertThat(ext).isNotNull();
    assertThat(ext.planType()).isEqualTo("NEW");
  }

  private BusinessApplication createApp() {
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
