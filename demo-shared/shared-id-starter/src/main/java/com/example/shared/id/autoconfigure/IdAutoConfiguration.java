package com.example.shared.id.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/1/9 12:59
 */
@AutoConfiguration
@Import({
  DistributedLockConfiguration.class,
  StatelessIdConfiguration.class,
  SegmentIdConfiguration.class,
  IdServiceConfiguration.class
})
public class IdAutoConfiguration {
}
