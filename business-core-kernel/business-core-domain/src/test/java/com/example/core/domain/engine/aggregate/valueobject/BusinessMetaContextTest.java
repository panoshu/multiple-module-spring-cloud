package com.example.core.domain.engine.aggregate.valueobject;

import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.types.CustomerNo;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BusinessMetaContext 外部协作幂等键")
class BusinessMetaContextTest {

  @Test
  @DisplayName("读取注入的 requestNo 事实")
  void requestNo_readsInjectedFact() {
    BusinessMetaContext context = BusinessMetaContext.of(contextFixture());

    context.extensionFacts().put(BusinessMetaContext.REQUEST_NO_FACT, "SE-9");

    assertThat(context.requestNo()).isEqualTo("SE-9");
  }

  @Test
  @DisplayName("未注入时返回 null（可空过渡）")
  void requestNo_absentReturnsNull() {
    BusinessMetaContext context = BusinessMetaContext.of(contextFixture());

    assertThat(context.requestNo()).isNull();
  }

  @Test
  @DisplayName("extensionFacts 可变，供编排器注入")
  void extensionFacts_isMutable() {
    BusinessMetaContext context = BusinessMetaContext.of(contextFixture());

    context.extensionFacts().put("k", "v");

    assertThat(context.extensionFacts()).containsEntry("k", "v");
  }

  private BusinessContext contextFixture() {
    return new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("C-001"), "客户",
      ProductNo.of("P-001"), "产品",
      PlanNo.of("PL-001"), "方案",
      OperatingMode.TRUSTEE_ONLY, AccountManager.CJP
    );
  }
}
