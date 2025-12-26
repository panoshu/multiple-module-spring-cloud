package com.example.share.logging.obfuscate.filter;

import com.example.share.logging.obfuscate.config.ObfuscateConfig;
import com.example.share.logging.obfuscate.config.ValidatedFieldConfig;
import com.example.share.logging.obfuscate.service.ValueObfuscate;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.zalando.logbook.HeaderFilter;
import org.zalando.logbook.HttpHeaders;

import java.util.Map;

@Slf4j
public class HeaderObfuscationFilter implements HeaderFilter {

  private final Map<String, ValidatedFieldConfig> headerRules;
  private final ValueObfuscate valueObfuscate;
  private final boolean enabled;

  public HeaderObfuscationFilter(ObfuscateConfig config, ValueObfuscate valueObfuscate) {
    this.headerRules = config.getHeaderRules();
    this.valueObfuscate = valueObfuscate;
    this.enabled = config.getGlobalConfig().enable();
    log.info("Initialized Header Filter with {} rules", headerRules.size());
  }

  @Override
  @Nonnull
  public HttpHeaders filter(@Nonnull HttpHeaders headers) {
    if (!enabled || headerRules.isEmpty() || headers.isEmpty()) {
      return headers;
    }

    try {
      return headers.apply((name, values) -> {
        ValidatedFieldConfig rule = headerRules.get(name.toLowerCase());

        if (rule == null) {
          return values;
        }

        if (log.isDebugEnabled()) {
          log.debug("Obfuscating Header: [{}], Strategy: [{}]", name, rule.strategy());
        }

        return values.stream()
          .map(v -> valueObfuscate.obfuscate(v, rule))
          .toList();
      });
    } catch (Exception e) {
      log.error("Header obfuscation failed unexpectedly", e);
      return headers;
    }
  }
}
