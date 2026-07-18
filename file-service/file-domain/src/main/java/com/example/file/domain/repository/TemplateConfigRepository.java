package com.example.file.domain.repository;

import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.types.BizType;
import com.example.file.types.TemplateConfigId;
import com.example.shared.domain.repository.Repository;

import java.util.Optional;

public interface TemplateConfigRepository extends Repository<TemplateConfig, TemplateConfigId> {
  Optional<TemplateConfig> findActive(BizType bizType);
  Optional<TemplateConfig> findByBizTypeAndVersion(BizType bizType, String version);
  Optional<TemplateConfig> findById(TemplateConfigId id);
  void save(TemplateConfig config);
}
