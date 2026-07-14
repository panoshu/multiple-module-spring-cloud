package com.example.core.domain.vauleobject.config;

import java.util.Map;

/**
 * 表单解析配置
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 12:12
 */
public record FormParsingConfig(
  String parseTemplateId,       // 底层文档中心认识的模板 ID
  boolean requireSchemaValidate,// 是否需要底层服务进行 Json Schema 格式校验
  Map<String, Object> splitRules// 拆分规则 (如：按机构号拆分、按批次拆分)
) {
}
