package core.domain.rule;

/**
 * 头信息映射规则 (基于绝对坐标)
 */
public record HeaderMapping(
  String cell,      // Excel 绝对坐标，如 "B1", "D2"
  String jsonPath,  // 如 "$.header.orderNo"
  FieldType type
) implements MappingRule {
}
