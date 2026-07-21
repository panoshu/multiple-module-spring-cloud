package com.example.core.domain.aggregate.valueobject.config;

/**
 * 事实提取器配置, 用于提起个性化业务信息
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 16:53
 */
public record ExtractorConfig(
  String extractorName // 提取器名称
) {
}
