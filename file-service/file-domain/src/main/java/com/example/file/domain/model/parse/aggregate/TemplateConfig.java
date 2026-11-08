package com.example.file.domain.model.parse.aggregate;

import com.example.shared.exception.CommonError;
import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.shared.exception.DomainException;
import com.example.file.domain.model.parse.aggregate.entity.SourceTemplateDef;
import com.example.file.domain.model.enums.ConfigStatus;
import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.model.enums.IdentifyMode;
import com.example.file.domain.model.parse.valueobject.config.*;
import com.example.file.types.BizType;
import com.example.file.types.TemplateCode;
import com.example.file.types.TemplateConfigId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TemplateConfig extends AggregateRoot<TemplateConfigId> {

  private final BizType bizType;
  private final String templateVersion;
  private final ErrorPolicy errorPolicy;
  private final CanonicalModelDef canonicalModel;
  private final List<ValidationRule> validationRules;
  private final List<DerivationRule> derivationRules;
  private final SplitConfig splitConfig;
  private final List<SourceTemplateDef> sourceTemplates;
  private String targetTemplateRef;
  private TargetMapping targetMapping;
  private ConfigStatus status;
  private LocalDateTime effectiveFrom;
  private LocalDateTime effectiveTo;
  /**
   * 上下文路由规则（批次3 A3）——可变扩展字段（attach 注入，不改构造签名）.
   */
  private java.util.List<com.example.file.domain.model.parse.valueobject.config.RoutingRule> routingRules = java.util.List.of();
  /**
   * 导出数据增强端点（批次5：设计文档 §7——业务服务实现的 enrichment 端点 URL，
   * 生成流程在模板填充前回调；空=无增强）.
   */
  private String enrichmentEndpoint;

  // 业务创建
  private TemplateConfig(TemplateConfigId id, BizType bizType, String version, ErrorPolicy errorPolicy,
                         CanonicalModelDef canonicalModel, List<ValidationRule> validationRules,
                         List<DerivationRule> derivationRules, SplitConfig splitConfig,
                         List<SourceTemplateDef> sourceTemplates, String userNo) {
    super(id, userNo);
    this.bizType = bizType;
    this.templateVersion = version;
    this.errorPolicy = errorPolicy;
    this.canonicalModel = canonicalModel;
    this.validationRules = List.copyOf(validationRules);
    this.derivationRules = List.copyOf(derivationRules);
    this.splitConfig = splitConfig;
    this.sourceTemplates = new ArrayList<>(sourceTemplates);
    this.status = ConfigStatus.DRAFT;
    this.effectiveFrom = LocalDateTime.now();
    this.validateInvariants();
  }

  // 数据库重建
  public TemplateConfig(TemplateConfigId id, BizType bizType, String version, ErrorPolicy errorPolicy,
                        CanonicalModelDef canonicalModel, List<ValidationRule> validationRules,
                        List<DerivationRule> derivationRules, SplitConfig splitConfig,
                        List<SourceTemplateDef> sourceTemplates, String targetTemplateRef,
                        TargetMapping targetMapping, ConfigStatus status,
                        LocalDateTime effectiveFrom, LocalDateTime effectiveTo,
                        String createdBy, String updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version1) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version1);
    this.bizType = bizType;
    this.templateVersion = version;
    this.errorPolicy = errorPolicy;
    this.canonicalModel = canonicalModel;
    this.validationRules = validationRules;
    this.derivationRules = derivationRules;
    this.splitConfig = splitConfig;
    this.sourceTemplates = new ArrayList<>(sourceTemplates);
    this.targetTemplateRef = targetTemplateRef;
    this.targetMapping = targetMapping;
    this.status = status;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.validateInvariants();
  }

  public static TemplateConfig create(TemplateConfigId id, BizType bizType, String version,
                                      ErrorPolicy errorPolicy, CanonicalModelDef canonicalModel,
                                      List<ValidationRule> validationRules, List<DerivationRule> derivationRules,
                                      SplitConfig splitConfig, List<SourceTemplateDef> sourceTemplates,
                                      String userNo) {
    if (bizType == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("bizType null");
    }
    if (errorPolicy == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("errorPolicy null");
    }
    if (canonicalModel == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("canonicalModel null");
    }
    if (splitConfig == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("splitConfig null");
    }
    if (sourceTemplates == null || sourceTemplates.isEmpty()) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("sourceTemplates empty");
    }
    return new TemplateConfig(id, bizType, version, errorPolicy, canonicalModel,
      validationRules, derivationRules, splitConfig, sourceTemplates, userNo);
  }

  public void activate() {
    if (this.status == ConfigStatus.DEPRECATED) {
      throw new DomainException(FileErrorCodes.CONFIG_INVALID).withUserDetail("Cannot activate deprecated config");
    }
    this.status = ConfigStatus.ACTIVE;
    this.effectiveFrom = LocalDateTime.now();
  }

  public void deprecate() {
    this.status = ConfigStatus.DEPRECATED;
    this.effectiveTo = LocalDateTime.now();
  }

  public java.util.List<com.example.file.domain.model.parse.valueobject.config.RoutingRule> routingRules() {
    return List.copyOf(routingRules);
  }

  /**
   * 注入路由规则（配置装配阶段）.
   */
  public void attachRoutingRules(java.util.List<com.example.file.domain.model.parse.valueobject.config.RoutingRule> rules) {
    this.routingRules = rules == null ? List.of() : List.copyOf(rules);
  }

  public String enrichmentEndpoint() {
    return enrichmentEndpoint;
  }

  public void attachEnrichmentEndpoint(String endpoint) {
    this.enrichmentEndpoint = (endpoint == null || endpoint.isBlank()) ? null : endpoint;
  }

  public Optional<SourceTemplateDef> findSourceTemplate(TemplateCode code) {
    return sourceTemplates.stream().filter(s -> s.id().equals(code)).findFirst();
  }

  public Optional<SourceTemplateDef> autoIdentify(List<String> headers) {
    for (SourceTemplateDef s : sourceTemplates) {
      if (s.identifyMode() == IdentifyMode.AUTO) {
        long matchCount = s.fingerprint().stream().filter(headers::contains).count();
        if (matchCount >= Math.max(1, s.fingerprint().size() / 2)) {
          return Optional.of(s);
        }
      }
    }
    return Optional.empty();
  }

  @Override
  protected void validateInvariants() {
    if (bizType == null) {
      throw new DomainException(FileErrorCodes.CONFIG_INVALID).withUserDetail("bizType null");
    }
    if (status == null) {
      throw new DomainException(FileErrorCodes.CONFIG_INVALID).withUserDetail("status null");
    }
  }

  public BizType bizType() {
    return bizType;
  }

  public String templateVersion() {
    return templateVersion;
  }

  public ErrorPolicy errorPolicy() {
    return errorPolicy;
  }

  public CanonicalModelDef canonicalModel() {
    return canonicalModel;
  }

  public List<ValidationRule> validationRules() {
    return validationRules;
  }

  public List<DerivationRule> derivationRules() {
    return derivationRules;
  }

  public SplitConfig splitConfig() {
    return splitConfig;
  }

  public List<SourceTemplateDef> sourceTemplates() {
    return List.copyOf(sourceTemplates);
  }

  public String targetTemplateRef() {
    return targetTemplateRef;
  }

  public TargetMapping targetMapping() {
    return targetMapping;
  }

  public ConfigStatus status() {
    return status;
  }

  public LocalDateTime effectiveFrom() {
    return effectiveFrom;
  }

  public LocalDateTime effectiveTo() {
    return effectiveTo;
  }
}
