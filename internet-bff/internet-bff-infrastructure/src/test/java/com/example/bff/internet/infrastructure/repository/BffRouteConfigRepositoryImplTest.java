package com.example.bff.internet.infrastructure.repository;

import com.example.bff.internet.infrastructure.TestApplication;
import com.example.bff.shared.infrastructure.entity.BffRouteConfigDO;
import com.example.bff.shared.infrastructure.mapper.BffRouteConfigMapper;
import com.example.bff.shared.infrastructure.repository.BffRouteConfigRepositoryImpl;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.ChannelScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

import static com.example.bff.shared.infrastructure.entity.table.BffRouteConfigDOTableDef.BFF_ROUTE_CONFIG_DO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestApplication.class)
@Transactional
class BffRouteConfigRepositoryImplTest {

  @Autowired
  private BffRouteConfigRepositoryImpl repository;

  @Autowired
  private BffRouteConfigMapper mapper;

  @Test
  @DisplayName("按业务类型和 ALL 渠道查找路由配置")
  void findByBusinessType_allScope() {
    insertRoute("ACC_PLAN_CREATE", "annuity-service", "ALL");

    Optional<BffRouteConfig> result = repository.findByBusinessType("ACC_PLAN_CREATE", ChannelScope.INTERNET);

    assertTrue(result.isPresent());
    assertEquals("annuity-service", result.get().serviceName());
    assertEquals(ChannelScope.ALL, result.get().channelScope());
  }

  @Test
  @DisplayName("按业务类型和指定渠道查找路由配置（优先于 ALL）")
  void findByBusinessType_specificScope() {
    insertRoute("LOAN_APPLY", "loan-service", "ALL");
    insertRoute("LOAN_APPLY", "loan-service-vip", "INTERNET");

    Optional<BffRouteConfig> result = repository.findByBusinessType("LOAN_APPLY", ChannelScope.INTERNET);

    assertTrue(result.isPresent());
    assertEquals("loan-service-vip", result.get().serviceName());
    assertEquals(ChannelScope.INTERNET, result.get().channelScope());
  }

  @Test
  @DisplayName("未知业务类型返回 empty")
  void findByBusinessType_unknownType() {
    Optional<BffRouteConfig> result = repository.findByBusinessType("UNKNOWN", ChannelScope.INTERNET);

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("禁用的路由配置不返回")
  void findByBusinessType_disabled() {
    insertRoute("DISABLED_TYPE", "some-service", "ALL");
    BffRouteConfigDO record = mapper.selectOneByQuery(
      com.mybatisflex.core.query.QueryWrapper.create()
        .where(BFF_ROUTE_CONFIG_DO.BUSINESS_TYPE.eq("DISABLED_TYPE")));
    record.setEnabled(false);
    mapper.update(record);

    Optional<BffRouteConfig> result = repository.findByBusinessType("DISABLED_TYPE", ChannelScope.INTERNET);

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("findAllServiceNames 返回去重的服务名集合")
  void findAllServiceNames() {
    insertRoute("TYPE_A", "annuity-service", "ALL");
    insertRoute("TYPE_B", "annuity-service", "ALL");
    insertRoute("TYPE_C", "loan-service", "ALL");

    Set<String> names = repository.findAllServiceNames();

    assertTrue(names.contains("annuity-service"));
    assertTrue(names.contains("loan-service"));
    assertEquals(2, names.size());
  }

  private void insertRoute(String businessType, String serviceName, String channelScope) {
    BffRouteConfigDO record = new BffRouteConfigDO();
    record.setBusinessType(businessType);
    record.setServiceName(serviceName);
    record.setChannelScope(channelScope);
    record.setEnabled(true);
    record.setDeleted(false);
    record.setVersion(0);
    mapper.insert(record);
  }
}
