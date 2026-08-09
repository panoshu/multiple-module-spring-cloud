package com.example.bff.shared.registry;

import com.example.core.api.batch.BusinessBatchApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class KernelApiRegistryTest {

  @Test
  @DisplayName("getBatchApi 返回非空代理")
  void getBatchApi_returnsProxy() {
    RestClient.Builder builder = RestClient.builder();
    KernelApiRegistry registry = new KernelApiRegistry(builder);

    BusinessBatchApi api = registry.getBatchApi("annuity-service");

    assertNotNull(api);
  }

  @Test
  @DisplayName("相同 serviceName 返回缓存的代理实例")
  void getBatchApi_cachesProxy() {
    RestClient.Builder builder = RestClient.builder();
    KernelApiRegistry registry = new KernelApiRegistry(builder);

    BusinessBatchApi first = registry.getBatchApi("annuity-service");
    BusinessBatchApi second = registry.getBatchApi("annuity-service");

    assertSame(first, second);
  }

  @Test
  @DisplayName("不同 serviceName 返回不同代理实例")
  void getBatchApi_differentServices() {
    RestClient.Builder builder = RestClient.builder();
    KernelApiRegistry registry = new KernelApiRegistry(builder);

    BusinessBatchApi annuityApi = registry.getBatchApi("annuity-service");
    BusinessBatchApi loanApi = registry.getBatchApi("loan-service");

    assertNotSame(annuityApi, loanApi);
  }

  @Test
  @DisplayName("contains 方法正确反映缓存状态")
  void contains_reflectsCache() {
    RestClient.Builder builder = RestClient.builder();
    KernelApiRegistry registry = new KernelApiRegistry(builder);

    assertFalse(registry.contains("annuity-service"));

    registry.getBatchApi("annuity-service");

    assertTrue(registry.contains("annuity-service"));
  }
}
