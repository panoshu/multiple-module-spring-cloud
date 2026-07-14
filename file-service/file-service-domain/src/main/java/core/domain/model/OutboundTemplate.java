package core.domain.model;

/**
 * 输出(渲染)模板聚合根
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/24 20:43
 */
public record OutboundTemplate(
  String templateId,           // 如: CORP_PLAN_STANDARD_OUT
  OutboundRule outboundRule    // 仅保留导出规则
) {
}
