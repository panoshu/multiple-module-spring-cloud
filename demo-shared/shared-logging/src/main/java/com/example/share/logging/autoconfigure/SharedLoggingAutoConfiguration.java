package com.example.share.logging.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2025/12/26 15:21
 */
@AutoConfiguration
@Import({
  LogbookDatabaseAutoConfiguration.class,
  LogbookObfuscationAutoConfiguration.class,
})
public class SharedLoggingAutoConfiguration {
}
