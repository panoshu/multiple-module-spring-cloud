package com.example.shared.client.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/1/9 12:53
 */
@AutoConfiguration
@Import({
  ClientInterceptorConfig.class,
  RetrofitLoggingConfig.class,
})
public class ClientAutoConfiguration {
}
