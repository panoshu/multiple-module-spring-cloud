package com.example.core.application.engine.service;

import com.example.core.domain.engine.aggregate.valueobject.BusinessTypeCode;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.gateway.ProcessDefinitionGateway;
import com.example.core.domain.engine.gateway.ProcessDefinitionStore;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * 组合式流程定义加载网关（spec §7.1 / §14）。
 * <p>
 * 优先从版本化定义存储读取（支持历史版本与最新发布版本），未命中时回退
 * 过渡期存量路由适配器，保证尚未迁移的业务类型仍可运行。
 * 业务服务提供自定义 {@link ProcessDefinitionGateway} 后，整个默认实现自动让位。
 */
@RequiredArgsConstructor
public class CompositeProcessDefinitionGateway implements ProcessDefinitionGateway {

  private final ProcessDefinitionStore store;
  private final ProcessDefinitionGateway fallback;

  @Override
  public Optional<ProcessDefinition> load(BusinessTypeCode businessType, DefinitionVersion version) {
    Optional<ProcessDefinition> stored = store.load(businessType, version);
    return stored.isPresent() ? stored : fallback.load(businessType, version);
  }

  @Override
  public Optional<ProcessDefinition> loadLatest(BusinessTypeCode businessType) {
    Optional<ProcessDefinition> stored = store.loadLatest(businessType);
    return stored.isPresent() ? stored : fallback.loadLatest(businessType);
  }
}
