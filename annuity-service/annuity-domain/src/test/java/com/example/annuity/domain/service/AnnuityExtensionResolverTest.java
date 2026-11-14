package com.example.annuity.domain.service;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.annuity.domain.repository.AnnuityApplicationExtensionStore;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.business.aggregate.valueobject.ApplicationSource;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.types.Channel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.types.OperatingMode;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AnnuityExtensionResolver 扩展字段解析器")
class AnnuityExtensionResolverTest {

  private final StubExtensionStore store = new StubExtensionStore();
  private final AnnuityExtensionResolver resolver = new AnnuityExtensionResolver(store);

  @Test
  @DisplayName("扩展字段缺失时抛出 DomainException")
  void resolve_nullExtensionThrows() {
    BusinessApplication app = createApp();

    assertThatThrownBy(() -> resolver.resolve(app))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("扩展字段存在时返回强类型实例")
  void resolve_correctTypeReturns() {
    BusinessApplication app = createApp();
    store.value = new AnnuityApplicationExtension(
      BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, false);

    AnnuityApplicationExtension ext = resolver.resolve(app);

    assertThat(ext).isNotNull();
    assertThat(ext.planType()).isEqualTo("NEW");
  }

  /** 测试替身：按 applicationId 关联的业务服务自有存储（§6.2）。 */
  private static final class StubExtensionStore implements AnnuityApplicationExtensionStore {
    private AnnuityApplicationExtension value;

    @Override
    public void save(ApplicationId applicationId, AnnuityApplicationExtension extension) {
      this.value = extension;
    }

    @Override
    public Optional<AnnuityApplicationExtension> load(ApplicationId applicationId) {
      return Optional.ofNullable(value);
    }
  }

  private BusinessApplication createApp() {
    BusinessContext context = new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("C-001"), "客户",
      ProductNo.of("P-001"), "产品",
      PlanNo.of("PL-001"), "方案",
      OperatingMode.TRUSTEE_ONLY, AccountManager.CJP
    );
    OperatorInfo operator = new OperatorInfo(
      Channel.NETAPP, "U-TEST", "操作人", false
    );
    return BusinessApplication.createFromForm(
      new ApplicationId("APP-001"),
      new ApplicationSource(new FormId("F-001"), BatchId.of("B-001")),
      context, operator, DefinitionVersion.initial()
    );
  }
}
