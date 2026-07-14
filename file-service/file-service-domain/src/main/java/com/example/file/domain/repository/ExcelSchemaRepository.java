package com.example.file.domain.repository;

import com.example.file.domain.model.schema.ExcelSchema;

// ==========================================
// 1. 配置加载端口 (Schema Repository)
// ==========================================
public interface ExcelSchemaRepository {
  // 默认提供从 Classpath YAML 加载的实现，后续可无缝替换为 DB 实现
  ExcelSchema loadSchema(String schemaId);
}
