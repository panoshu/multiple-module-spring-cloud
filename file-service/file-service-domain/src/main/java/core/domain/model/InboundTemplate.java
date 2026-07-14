package core.domain.model;

/**
 * 输入(解析)模板聚合根
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/24 20:42
 */
public record InboundTemplate(
  String templateId,           // 如: CORP_PLAN_CLIENT_A_IN
  String outboundTemplateId,     // 【关键点】指向统一的输出模板ID (如: CORP_PLAN_STANDARD_OUT)
  InboundRule inboundRule,     // 仅保留读取规则
  String jsonSchema            // Schema 主要用于校验输入，放在这里
) {
}
