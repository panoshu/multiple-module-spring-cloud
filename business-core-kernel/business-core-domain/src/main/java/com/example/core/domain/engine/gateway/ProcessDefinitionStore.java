package com.example.core.domain.engine.gateway;

import com.example.core.domain.engine.aggregate.valueobject.BusinessTypeCode;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;

import java.util.Optional;

/**
 * 版本化流程定义存储（spec §7.1 / §14 / §19.1）。
 * <p>
 * 与只读的 {@link ProcessDefinitionGateway} 分离：本契约负责「发布」，
 * 存储保证「已发布定义不可变」与「历史版本可读」，并维护最新版本指针
 * （发布即缓存失效）。定义按 businessType + version 唯一标识。
 */
public interface ProcessDefinitionStore {

  /** 发布不可变定义；重复版本必须抛 {@code DomainException}。 */
  void publish(ProcessDefinition definition);

  Optional<ProcessDefinition> load(BusinessTypeCode businessType, DefinitionVersion version);

  Optional<ProcessDefinition> loadLatest(BusinessTypeCode businessType);
}
