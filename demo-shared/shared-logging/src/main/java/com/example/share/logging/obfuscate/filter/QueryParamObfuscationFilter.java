package com.example.share.logging.obfuscate.filter;

import com.example.share.logging.obfuscate.config.ObfuscateConfig;
import com.example.share.logging.obfuscate.config.ValidatedFieldConfig;
import com.example.share.logging.obfuscate.strategy.ObfuscationStrategyFactory;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.zalando.logbook.QueryFilter;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class QueryParamObfuscationFilter extends BaseObfuscationFilter implements QueryFilter {

  private final Map<String, ValidatedFieldConfig> queryRules;

  public QueryParamObfuscationFilter(ObfuscateConfig config,
                                     ObfuscationStrategyFactory strategyFactory) {
    super(config, strategyFactory);
    this.queryRules = config.getQueryRules();
    log.debug("Initialized query parameter obfuscation with {} rules", queryRules.size());
  }

  @Override
  public String filter(@Nonnull String query) {
    if (!this.config.getGlobalConfig().enable() || query.isEmpty() || queryRules.isEmpty()) {
      return query;
    }

    try {
      return obfuscateQueryParams(query);
    } catch (Exception e) {
      log.error("Failed to obfuscate query parameters: {}", e.getMessage(), e);
      return query;
    }
  }

  private String obfuscateQueryParams(String query) {
    if (!query.contains("=")) {
      return query;
    }

    String[] params = query.split("&");
    boolean modified = false;
    StringBuilder result = new StringBuilder(query.length());

    for (int i = 0; i < params.length; i++) {
      String param = params[i];
      if (param.isEmpty()) continue;

      String[] parts = param.split("=", 2);
      String paramName = decode(parts[0]);
      String paramValue = parts.length > 1 ? decode(parts[1]) : "";

      if (StringUtils.hasText(paramName) && StringUtils.hasText(paramValue)) {
        ValidatedFieldConfig fieldConfig = queryRules.get(paramName.toLowerCase());
        if (fieldConfig != null) {
          String obfuscatedValue = obfuscateValue(paramValue, fieldConfig);
          if (!paramValue.equals(obfuscatedValue)) {
            paramValue = obfuscatedValue;
            modified = true;
          }
        }
      }

      if (i > 0) result.append('&');
      result.append(encode(parts[0]));
      if (StringUtils.hasText(paramValue)) {
        result.append('=').append(encode(paramValue));
      }
    }

    return modified ? result.toString() : query;
  }

  private String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8)
      .replace("+", "%20")
      .replace("*", "%2A")
      .replace("%7E", "~");
  }
}
