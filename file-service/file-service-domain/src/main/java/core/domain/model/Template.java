package core.domain.model;


/**
 * 模板配置聚合根
 */
public record Template(
  String templateId,          // 模板唯一ID (如 INVENTORY_RECEIPT_V1)
  String businessType,        // 业务类型
  InboundRule inboundRule,    // 解析(读取)规则
  OutboundRule outboundRule,  // 渲染(导出)规则
  String jsonSchema           // 标准的 JSON Schema 原文 (用于底层校验器)
) {
}
