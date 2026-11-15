package com.example.auth.infrastructure.product.config;

import com.example.auth.domain.product.PlanSnapshot;
import com.example.auth.infrastructure.product.gateway.InMemoryProductGateway;
import com.example.core.types.CustomerNo;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 内存组织目录的条件装配（立项批次 67）。
 * <p>
 * 默认不注册：{@code StubProductGateway} 保持唯一 {@code ProductGateway} 实现，
 * 所有依赖它的领域服务与 E2E 行为不变。配置开启后以 {@code @Primary} 覆盖桩，
 * 计划层级数据来自 {@link PlanDirectoryProperties}。
 *
 * @author auth-infrastructure
 */
@Configuration
@EnableConfigurationProperties(PlanDirectoryProperties.class)
public class OrgDirectoryConfig {

  @Bean
  @Primary
  @ConditionalOnProperty(prefix = "auth.org-directory", name = "enabled", havingValue = "true")
  public InMemoryProductGateway inMemoryProductGateway(PlanDirectoryProperties properties) {
    List<PlanSnapshot> snapshots = properties.getPlans().stream()
      .map(OrgDirectoryConfig::toSnapshot)
      .toList();
    return new InMemoryProductGateway(snapshots);
  }

  private static PlanSnapshot toSnapshot(PlanDirectoryProperties.PlanDef def) {
    return new PlanSnapshot(
      PlanNo.of(def.getPlanNo()),
      ProductNo.of(def.getProductNo()),
      CustomerNo.of(def.getCustomerNo()),
      Optional.ofNullable(def.getParentPlanNo()).map(PlanNo::of),
      def.getName(),
      Instant.EPOCH);
  }
}
