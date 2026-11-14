package com.example.core.infrastructure.business.converter;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.ApplicationSource;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.infrastructure.business.entity.BusinessApplicationDO;
import com.example.core.types.ApplicationId;
import com.example.core.types.BatchId;
import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.FormId;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.file.types.FileId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BusinessApplicationConverter 定义版本映射")
class BusinessApplicationConverterTest {

  private BusinessApplicationConverter converter;

  @BeforeEach
  void setUp() {
    converter = new BusinessApplicationConverterImpl();
  }

  @Test
  @DisplayName("定义版本在 DO 与领域对象之间往返")
  void definitionVersionRoundTrip() {
    BusinessApplication app = BusinessApplication.createFromForm(
      new ApplicationId("APP-001"),
      new ApplicationSource(new FormId("F-001"), BatchId.of("B-001")),
      context(), operator(), new FileId("FILE-001"), DefinitionVersion.of("v2")
    );

    BusinessApplicationDO aDo = converter.toDO(app);
    BusinessApplication restored = converter.toDomain(aDo);

    assertThat(aDo.getDefinitionVersion()).isEqualTo("v2");
    assertThat(restored.definitionVersion()).isEqualTo(DefinitionVersion.of("v2"));
  }

  @Test
  @DisplayName("无版本列的存量数据回退到初始版本")
  void legacyRowFallsBackToInitialVersion() {
    BusinessApplication app = BusinessApplication.createFromForm(
      new ApplicationId("APP-001"),
      new ApplicationSource(new FormId("F-001"), BatchId.of("B-001")),
      context(), operator(), new FileId("FILE-001"), DefinitionVersion.initial()
    );
    BusinessApplicationDO aDo = converter.toDO(app);
    aDo.setDefinitionVersion(null);

    BusinessApplication restored = converter.toDomain(aDo);

    assertThat(restored.definitionVersion()).isEqualTo(DefinitionVersion.initial());
  }

  private BusinessContext context() {
    return new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("C-001"), "客户",
      ProductNo.of("P-001"), "产品",
      PlanNo.of("PL-001"), "方案",
      OperatingMode.TRUSTEE_ONLY, AccountManager.CJP
    );
  }

  private OperatorInfo operator() {
    return new OperatorInfo(Channel.NETAPP, "U-TEST", "操作人", false);
  }
}
