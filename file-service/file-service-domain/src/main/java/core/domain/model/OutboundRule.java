package core.domain.model;

import java.util.List;

// 写入规则通常与 InboundRule 结构相似，但映射字段可能更多。
// 这里保持一致的结构定义，方便适配"读写不对称"
public record OutboundRule(
  ExportEngineType engineType,         // ★ 新增：指定渲染引擎类型
  String baseTemplatePath,             // 物理模板路径 (仅当 engineType == TEMPLATE 时生效)
  List<StaticTextMapping> staticTexts, // 新增：用于在导出时画出静态的标签、标题等，还原完整的表单感
  HeaderZone headerZone,
  DetailZone detailZone
) {

  public ExportEngineType getSafeEngineType() {
    return engineType != null ? engineType : ExportEngineType.STREAM;
  }
}
