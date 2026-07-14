package core.domain.rule;

/**
 * 映射规则的密封接口
 * 明确规定只有 HeaderMapping 和 DetailMapping 两种映射类型
 */
public sealed interface MappingRule permits HeaderMapping, DetailMapping {
  String jsonPath(); // 目标 JSON 路径

  FieldType type();  // 字段类型定义
}
