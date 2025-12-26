package com.example.shared.core.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2025/12/26 13:27
 */
@AutoConfiguration
@Import(InfrastructureConfiguration.class)
public class SharedCoreAutoConfiguration {
}
