package com.example.share.logging.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Shared Logging 主配置入口
 */
@AutoConfiguration
@Import({
  LogCoreConfiguration.class,         // 核心域：Pipeline
  LogSanitizationConfiguration.class, // 脱敏域：Sanitizer
  LogExportConfiguration.class,       // 导出域：Exporter
  LogbookIntegrationConfiguration.class // 集成域：Logbook Adapter
})
public class SharedLoggingAutoConfiguration {
}
