package core.domain.model;

import core.domain.rule.DetailMapping;
import core.domain.rule.DynamicDetailMapping;

import java.util.List;

public record DetailZone(
  int startRow,                           // 明细数据起始行（如 8）
  int fieldIdRow,                         // ★ 新增：字段ID（英文）写入的物理行号（如 5）
  int titleRow,                           // ★ 新增：中文名称写入的物理行号（如 7）
  String endRowMarker,
  List<String> uniqueKeys,
  List<DetailMapping> fields,
  DynamicDetailMapping dynamicFields
) {
}
