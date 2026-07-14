package core.domain.model;

import core.domain.rule.HeaderMapping;

import java.util.List;

public record HeaderZone(
  int endRow,                 // 表头区域结束行，引擎到达此行后立即触发 Header Validation (Fail-Fast)
  List<HeaderMapping> fields  // 表头字段映射集合
) {
}
