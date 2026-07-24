package com.example.shared.web.autoconfigure;

import com.example.shared.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/1/9 13:08
 */
@AutoConfiguration
@Import({
  BizTraceAutoConfiguration.class,
  GlobalExceptionHandler.class
})
public class WebAutoConfiguration {
}
