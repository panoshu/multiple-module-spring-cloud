package com.example.file.infrastructure.excel.persistence;

import com.example.file.domain.model.locator.*;
import com.example.file.domain.model.region.DiscreteRegionConfig;
import com.example.file.domain.model.region.HorizontalTableRegionConfig;
import com.example.file.domain.model.region.RegionConfig;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.repository.ExcelSchemaRepository;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;

/**
 * YamlExcelSchemaRepositoryAdapter
 * * 基于 YAML 的配置仓库实现，内置无侵入的 Jackson 多态反序列化支持
 */
public class YamlExcelSchemaRepositoryAdapter implements ExcelSchemaRepository {

  private final ObjectMapper yamlMapper;

  public YamlExcelSchemaRepositoryAdapter() {
    this.yamlMapper = new ObjectMapper(new YAMLFactory());
    this.yamlMapper.findAndRegisterModules(); // 支持 LocalDate 等 JDK8 时间

    // 🟢 注册 MixIn 配置，实现多态反序列化，保持领域模型纯洁性 (不被 Jackson 注解污染)
    this.yamlMapper.addMixIn(RegionConfig.class, RegionConfigMixin.class);
    this.yamlMapper.addMixIn(Locator.class, LocatorMixin.class);
  }

  @Override
  public ExcelSchema loadSchema(String schemaId) {
    String fileName = "/schemas/" + schemaId + ".yaml";
    InputStream in = getClass().getResourceAsStream(fileName);

    // 🟢 1. 将空检查提取到 try-catch 外部，防止 IllegalArgumentException 被错误包装
    if (in == null) {
      throw new IllegalArgumentException("找不到指定的 Schema 配置文件: " + fileName);
    }

    // 2. 正常读取并只捕获真正的解析异常
    try (in) {
      return yamlMapper.readValue(in, ExcelSchema.class);
    } catch (Exception e) {
      throw new RuntimeException("解析 Schema 配置文件失败: " + schemaId, e);
    }
  }

  // ==========================================
  // Jackson 多态反序列化 MixIn 内部接口配置
  // 告诉 Jackson 根据 yaml 中的属性值动态实例化哪个子类
  // ==========================================

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = HorizontalTableRegionConfig.class, name = "HORIZONTAL_TABLE"),
    @JsonSubTypes.Type(value = DiscreteRegionConfig.class, name = "DISCRETE")
  })
  private interface RegionConfigMixin {
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "strategy")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = HeaderMatchLocator.class, name = "HEADER_MATCH"),
    @JsonSubTypes.Type(value = AbsoluteLocator.class, name = "ABSOLUTE"),
    @JsonSubTypes.Type(value = AnchorRelativeLocator.class, name = "ANCHOR_RELATIVE"),
    @JsonSubTypes.Type(value = RegionRelativeLocator.class, name = "REGION_RELATIVE") // 🟢 注册新定位策略
  })
  private interface LocatorMixin {
  }
}
