package com.example.file.domain.model.aggregate.entity;

import com.example.file.domain.model.enums.IdentifyMode;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.types.TemplateCode;
import com.example.shared.domain.aggregate.entity.Entity;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 源模板定义（聚合内实体，脱离 TemplateConfig 没有独立含义）
 */
public class SourceTemplateDef extends Entity<TemplateCode> {

  private IdentifyMode identifyMode;
  private List<String> fingerprint;
  private List<RegionDef> regions;

  // 业务创建
  public SourceTemplateDef(TemplateCode templateCode, IdentifyMode mode,
                           List<String> fingerprint, List<RegionDef> regions, UserNo userNo) {
    super(templateCode, userNo);
    if (mode == null) throw new IllegalArgumentException("identifyMode null");
    if (regions == null || regions.isEmpty()) throw new IllegalArgumentException("regions empty");
    this.identifyMode = mode;
    this.fingerprint = fingerprint == null ? List.of() : List.copyOf(fingerprint);
    this.regions = List.copyOf(regions);
  }

  // 数据库重建
  public SourceTemplateDef(TemplateCode id, IdentifyMode identifyMode, List<String> fingerprint,
                           List<RegionDef> regions, UserNo createdBy, UserNo updatedBy,
                           LocalDateTime createdAt, LocalDateTime updatedAt,
                           com.example.shared.domain.aggregate.valueobject.Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.identifyMode = identifyMode;
    this.fingerprint = fingerprint;
    this.regions = regions;
  }

  public IdentifyMode identifyMode() { return identifyMode; }
  public List<String> fingerprint() { return fingerprint; }
  public List<RegionDef> regions() { return regions; }

  @Override
  protected void validateInvariants() {
    if (identifyMode == null) throw new IllegalStateException("identifyMode null");
    if (regions == null || regions.isEmpty()) throw new IllegalStateException("regions empty");
  }
}
