package core.domain.model;

public enum ErrorPhase {
  HEADER_PARSING,    // 表头解析/提取阶段
  HEADER_VALIDATION, // 表头 Schema 校验阶段
  DETAIL_PARSING,    // 明细解析/提取阶段
  DETAIL_VALIDATION  // 明细 Schema 校验阶段
}
