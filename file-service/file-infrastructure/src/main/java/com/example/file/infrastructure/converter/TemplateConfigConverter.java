package com.example.file.infrastructure.converter;

import com.example.file.domain.model.aggregate.entity.SourceTemplateDef;
import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.domain.model.enums.ConfigStatus;
import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.model.valueobject.config.*;
import com.example.file.infrastructure.entity.TemplateConfigDO;
import com.example.file.types.BizType;
import com.example.file.types.TemplateConfigId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TemplateConfigConverter {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

  @Mapping(target = "id", expression = "java(config.id().value())")
  @Mapping(target = "bizType", expression = "java(bizTypeToString(config.bizType()))")
  @Mapping(target = "templateVersion", expression = "java(config.templateVersion())")
  @Mapping(target = "errorPolicy", expression = "java(errorPolicyToString(config.errorPolicy()))")
  @Mapping(target = "canonicalModel", expression = "java(canonicalModelToJson(config.canonicalModel()))")
  @Mapping(target = "validationRules", expression = "java(validationRuleListToJson(config.validationRules()))")
  @Mapping(target = "derivationRules", expression = "java(derivationRuleListToJson(config.derivationRules()))")
  @Mapping(target = "splitConfig", expression = "java(splitConfigToJson(config.splitConfig()))")
  @Mapping(target = "sourceTemplates", expression = "java(sourceTemplateListToJson(config.sourceTemplates()))")
  @Mapping(target = "targetTemplateRef", expression = "java(config.targetTemplateRef())")
  @Mapping(target = "targetMapping", expression = "java(targetMappingToJson(config.targetMapping()))")
  @Mapping(target = "status", expression = "java(configStatusToString(config.status()))")
  @Mapping(target = "effectiveFrom", expression = "java(config.effectiveFrom())")
  @Mapping(target = "effectiveTo", expression = "java(config.effectiveTo())")
  @Mapping(target = "createdBy", expression = "java(config.createdBy().value())")
  @Mapping(target = "updatedBy", expression = "java(config.updatedBy().value())")
  @Mapping(target = "createTime", expression = "java(config.createdAt())")
  @Mapping(target = "updateTime", expression = "java(config.updatedAt())")
  @Mapping(target = "version", expression = "java((int) config.version().value())")
  @Mapping(target = "deleted", constant = "false")
  TemplateConfigDO toDO(TemplateConfig config);

  @Mapping(target = "id", source = "id", qualifiedByName = "toTemplateConfigId")
  @Mapping(target = "bizType", source = "bizType", qualifiedByName = "stringToBizType")
  @Mapping(target = "errorPolicy", source = "errorPolicy", qualifiedByName = "stringToErrorPolicy")
  @Mapping(target = "canonicalModel", source = "canonicalModel", qualifiedByName = "jsonToCanonicalModel")
  @Mapping(target = "validationRules", source = "validationRules", qualifiedByName = "jsonToValidationRuleList")
  @Mapping(target = "derivationRules", source = "derivationRules", qualifiedByName = "jsonToDerivationRuleList")
  @Mapping(target = "splitConfig", source = "splitConfig", qualifiedByName = "jsonToSplitConfig")
  @Mapping(target = "sourceTemplates", source = "sourceTemplates", qualifiedByName = "jsonToSourceTemplateList")
  @Mapping(target = "targetMapping", source = "targetMapping", qualifiedByName = "jsonToTargetMapping")
  @Mapping(target = "status", source = "status", qualifiedByName = "stringToConfigStatus")
  @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "toUserNo")
  @Mapping(target = "updatedBy", source = "updatedBy", qualifiedByName = "toUserNo")
  @Mapping(target = "createdAt", source = "createTime")
  @Mapping(target = "updatedAt", source = "updateTime")
  @Mapping(target = "version1", source = "version", qualifiedByName = "toVersion")
  @Mapping(target = "domainEvents", ignore = true)
  TemplateConfig toDomain(TemplateConfigDO aDo);

  @Named("toTemplateConfigId")
  default TemplateConfigId toTemplateConfigId(String id) {
    return id != null ? TemplateConfigId.of(id) : null;
  }

  @Named("toUserNo")
  default UserNo toUserNo(String userNo) {
    return userNo != null ? UserNo.of(userNo) : null;
  }

  @Named("toVersion")
  default Version toVersion(Integer version) {
    return version != null ? Version.of(version.longValue()) : null;
  }

  default String bizTypeToString(BizType bizType) {
    return bizType != null ? bizType.value() : null;
  }

  @Named("stringToBizType")
  default BizType stringToBizType(String bizType) {
    return bizType != null ? BizType.of(bizType) : null;
  }

  default String errorPolicyToString(ErrorPolicy errorPolicy) {
    return errorPolicy != null ? errorPolicy.name() : null;
  }

  @Named("stringToErrorPolicy")
  default ErrorPolicy stringToErrorPolicy(String errorPolicy) {
    return errorPolicy != null ? ErrorPolicy.valueOf(errorPolicy) : null;
  }

  default String configStatusToString(ConfigStatus status) {
    return status != null ? status.name() : null;
  }

  @Named("stringToConfigStatus")
  default ConfigStatus stringToConfigStatus(String status) {
    return status != null ? ConfigStatus.valueOf(status) : null;
  }

  default String canonicalModelToJson(CanonicalModelDef canonicalModel) {
    if (canonicalModel == null) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(canonicalModel);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化标准模型定义失败", e);
    }
  }

  @Named("jsonToCanonicalModel")
  default CanonicalModelDef jsonToCanonicalModel(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readValue(json, CanonicalModelDef.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化标准模型定义失败", e);
    }
  }

  default String validationRuleListToJson(List<ValidationRule> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(list);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化校验规则列表失败", e);
    }
  }

  @Named("jsonToValidationRuleList")
  default List<ValidationRule> jsonToValidationRuleList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<List<ValidationRule>>() {
      });
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化校验规则列表失败", e);
    }
  }

  default String derivationRuleListToJson(List<DerivationRule> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(list);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化派生规则列表失败", e);
    }
  }

  @Named("jsonToDerivationRuleList")
  default List<DerivationRule> jsonToDerivationRuleList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<List<DerivationRule>>() {
      });
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化派生规则列表失败", e);
    }
  }

  default String splitConfigToJson(SplitConfig splitConfig) {
    if (splitConfig == null) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(splitConfig);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化拆分配置失败", e);
    }
  }

  @Named("jsonToSplitConfig")
  default SplitConfig jsonToSplitConfig(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readValue(json, SplitConfig.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化拆分配置失败", e);
    }
  }

  default String sourceTemplateListToJson(List<SourceTemplateDef> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(list);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化源模板列表失败", e);
    }
  }

  @Named("jsonToSourceTemplateList")
  default List<SourceTemplateDef> jsonToSourceTemplateList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<List<SourceTemplateDef>>() {
      });
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化源模板列表失败", e);
    }
  }

  default String targetMappingToJson(TargetMapping targetMapping) {
    if (targetMapping == null) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(targetMapping);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化目标映射失败", e);
    }
  }

  @Named("jsonToTargetMapping")
  default TargetMapping jsonToTargetMapping(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readValue(json, TargetMapping.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化目标映射失败", e);
    }
  }
}
