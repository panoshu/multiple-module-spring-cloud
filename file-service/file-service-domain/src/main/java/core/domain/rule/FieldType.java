package core.domain.rule;

public enum FieldType {
  STRONG, // 强类型：需要严格的数据转换，参与 Schema 校验
  WEAK    // 弱类型：兜底收容，作为 String 或原生 Object 塞入动态 Map
}
