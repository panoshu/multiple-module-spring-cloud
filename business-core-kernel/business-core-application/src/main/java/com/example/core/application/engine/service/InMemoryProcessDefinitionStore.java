package com.example.core.application.engine.service;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.engine.aggregate.valueobject.BusinessTypeCode;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.gateway.ProcessDefinitionStore;
import com.example.shared.exception.DomainException;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内版本化流程定义存储（spec §7.1 / §19.1）。
 * <p>
 * 只缓存「已发布定义」（key 含业务类型与版本），不缓存流程当前状态；
 * 发布即更新 latest 指针，等价于缓存失效。定义本身不可变，存储不提供修改。
 * <p>
 * 进程内实现不依赖 Redis，宿主无需引入 shared-cache-starter；需要跨实例共享或
 * 多级缓存时，可提供自定义 {@link ProcessDefinitionStore} 覆盖本默认实现。
 */
@Slf4j
public class InMemoryProcessDefinitionStore implements ProcessDefinitionStore {

  private final Map<String, ProcessDefinition> definitions = new ConcurrentHashMap<>();
  private final Map<BusinessTypeCode, ProcessDefinition> latest = new ConcurrentHashMap<>();

  @Override
  public void publish(ProcessDefinition definition) {
    if (definition == null) {
      throw new DomainException(CoreDomainErrorCode.PROCESS_DEFINITION_INVALID)
        .withLogDetail("待发布流程定义不能为空");
    }
    String key = key(definition.businessType(), definition.version());
    ProcessDefinition existing = definitions.putIfAbsent(key, definition);
    if (existing != null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_OPERATION)
        .withLogDetail("已发布定义不可变，重复版本: " + key);
    }
    latest.put(definition.businessType(), definition);
    log.info("流程定义已发布: businessType={}, version={}",
      definition.businessType().value(), definition.version().value());
  }

  @Override
  public Optional<ProcessDefinition> load(BusinessTypeCode businessType, DefinitionVersion version) {
    if (businessType == null || version == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(definitions.get(key(businessType, version)));
  }

  @Override
  public Optional<ProcessDefinition> loadLatest(BusinessTypeCode businessType) {
    if (businessType == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(latest.get(businessType));
  }

  private static String key(BusinessTypeCode businessType, DefinitionVersion version) {
    return businessType.value() + ":" + version.value();
  }
}
