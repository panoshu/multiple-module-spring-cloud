package core.domain.model;

/**
 * ExportEngineType
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/24 22:36
 */
public enum ExportEngineType {
  STREAM,   // 流式引擎 (EasyExcel)：速度快，内存低，适用于单纯的数据导出
  TEMPLATE  // 模板引擎 (POI DOM)：高保真，保留所有排版和富文本，适用于精美报表
}
