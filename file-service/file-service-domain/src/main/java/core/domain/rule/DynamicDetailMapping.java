package core.domain.rule;

/**
 * 动态列兜底配置 (弱类型专属，处理未来可能增加的未知表单字段)
 */
public record DynamicDetailMapping(
  String startCol, // 从哪一列开始属于动态扩展字段，如 "C"
  String jsonPath  // 存放动态扩展数据的 Map 路径，如 "$.details[*].attributes"
) {
}
