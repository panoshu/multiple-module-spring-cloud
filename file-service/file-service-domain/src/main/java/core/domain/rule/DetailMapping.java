package core.domain.rule;

/**
 * 明细信息映射规则 (基于列号)
 */
public record DetailMapping(
  String col,
  String jsonPath,
  FieldType type,
  String fieldId,     // ★ 新增：字段英文ID（如 "XH", "XM"），用于写回输出表单的字段ID行
  String label,       // 字段业务中文名（用于错题本提示）
  String exportTitle  // 导出中文名（如 "序号*", "个人姓名*"）
) implements MappingRule {
}
