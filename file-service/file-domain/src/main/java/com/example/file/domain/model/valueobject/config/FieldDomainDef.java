package com.example.file.domain.model.valueobject.config;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 字段域定义（2026-11-08 批次4：设计文档 §7 字段域=标准数据模型）.
 *
 * <p>业务类型的字段全集（大而全），字段分两类来源：</p>
 * <ul>
 *   <li>{@code base}：来自解析原始数据（SubTaskData 直取）</li>
 *   <li>{@code enriched}：来自业务增强（导出时回调业务服务的 enricher 端点实时计算）</li>
 * </ul>
 *
 * <p><b>多入一出枢纽</b>：入站源模板映射以字段域为靶子（多样源→同一契约子集），
 * 出站导出模板从字段域取全集。配置校验器可据此发现"源模板映射了域外字段"的错误.</p>
 *
 * @param fields 字段定义清单（名字+来源+类型描述）
 */
public record FieldDomainDef(List<FieldDef> fields) implements ValueObject {

  public FieldDomainDef {
    fields = fields == null ? List.of() : List.copyOf(fields);
  }

  /**
   * 字段定义.
   *
   * @param name     字段名（canonical 契约名）
   * @param source   来源：base（解析原始）/ enriched（业务增强）
   * @param typeHint 类型描述（展示用）
   */
  public record FieldDef(String name, FieldSource source, String typeHint) implements ValueObject {
  }

  public enum FieldSource {
    BASE, ENRICHED
  }

  public Set<String> baseFieldNames() {
    return fields.stream()
      .filter(f -> f.source() == FieldSource.BASE)
      .map(FieldDef::name)
      .collect(Collectors.toUnmodifiableSet());
  }

  public Set<String> enrichedFieldNames() {
    return fields.stream()
      .filter(f -> f.source() == FieldSource.ENRICHED)
      .map(FieldDef::name)
      .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * 合并增强结果到 base 行（enriched 覆盖 base 同名字段——设计文档声明式合并规则）.
   */
  public Map<String, Object> merge(Map<String, Object> baseRow, Map<String, Object> enrichedFields) {
    if (enrichedFields == null || enrichedFields.isEmpty()) {
      return baseRow;
    }
    Set<String> allowed = enrichedFieldNames();
    var merged = new java.util.LinkedHashMap<>(baseRow);
    enrichedFields.entrySet().stream()
      .filter(e -> allowed.isEmpty() || allowed.contains(e.getKey()))
      .forEach(e -> merged.put(e.getKey(), e.getValue()));
    return merged;
  }
}
