package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BffBusinessTypeResponse;
import com.example.bff.shared.business.BusinessScenarioHandler;
import com.example.bff.shared.business.ScenarioHandlerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B-03：业务类型清单由 ScenarioHandlerRegistry 汇总。
 */
@DisplayName("SystemManagementService 业务类型清单")
class SystemManagementServiceTest {

  @Test
  @DisplayName("按 handler 声明返回业务类型、服务名与渠道范围")
  void listBusinessTypes_aggregatesHandlers() {
    BusinessScenarioHandler annuity = mock(BusinessScenarioHandler.class);
    when(annuity.supportedTypes()).thenReturn(Set.of("ANNUITY_OPEN", "ANNUITY_CHANGE"));
    when(annuity.serviceName()).thenReturn("annuity-service");
    ScenarioHandlerRegistry registry = new ScenarioHandlerRegistry(List.of(annuity));
    MockEnvironment environment = new MockEnvironment()
      .withProperty("bff.channel-scope", "INTRANET");

    SystemManagementService service = new SystemManagementService(environment, registry);

    List<BffBusinessTypeResponse> types = service.listBusinessTypes().data();

    assertThat(types).containsExactly(
      new BffBusinessTypeResponse("ANNUITY_CHANGE", "annuity-service", "INTRANET"),
      new BffBusinessTypeResponse("ANNUITY_OPEN", "annuity-service", "INTRANET"));
  }
}
