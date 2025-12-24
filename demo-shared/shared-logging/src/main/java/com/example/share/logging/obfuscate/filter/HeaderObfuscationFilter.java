package com.example.share.logging.obfuscate.filter;


import com.example.share.logging.obfuscate.config.ObfuscateConfig;
import com.example.share.logging.obfuscate.config.ValidatedFieldConfig;
import com.example.share.logging.obfuscate.strategy.ObfuscationStrategyFactory;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.zalando.logbook.HeaderFilter;
import org.zalando.logbook.HttpHeaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HeaderObfuscationFilter extends BaseObfuscationFilter implements HeaderFilter {

  private final Map<String, ValidatedFieldConfig> headerRules;

  public HeaderObfuscationFilter(ObfuscateConfig config,
                                 ObfuscationStrategyFactory strategyFactory) {
    super(config, strategyFactory);
    this.headerRules = config.getHeaderRules();
    log.debug("Initialized header obfuscation with {} rules", headerRules.size());
  }

  @Override
  public HttpHeaders filter(@Nonnull HttpHeaders headers) {
    if (!this.config.getGlobalConfig().enable() || headerRules.isEmpty() || headers.isEmpty()) {
      return headers;
    }

    try {
      Map<String, List<String>> filteredHeaders = new HashMap<>();
      headers.forEach((name, values) -> {
        String lowerName = name.toLowerCase();
        ValidatedFieldConfig fieldConfig = headerRules.get(lowerName);

        if (fieldConfig != null && !values.isEmpty()) {
          List<String> obfuscatedValues = new ArrayList<>(values.size());
          for (String value : values) {
            if (value != null) {
              obfuscatedValues.add(obfuscateValue(value, fieldConfig));
            }
          }
          filteredHeaders.put(name, obfuscatedValues);
        } else {
          filteredHeaders.put(name, new ArrayList<>(values));
        }
      });

      return HttpHeaders.of(filteredHeaders);
    } catch (Exception e) {
      log.error("Failed to obfuscate headers: {}", e.getMessage(), e);
      return headers;
    }
  }
}
