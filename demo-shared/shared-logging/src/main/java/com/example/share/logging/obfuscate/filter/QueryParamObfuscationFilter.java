package com.example.share.logging.obfuscate.filter;

import com.example.share.logging.obfuscate.config.ObfuscateConfig;
import com.example.share.logging.obfuscate.config.ValidatedFieldConfig;
import com.example.share.logging.obfuscate.service.ValueObfuscate;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.zalando.logbook.QueryFilter;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class QueryParamObfuscationFilter implements QueryFilter {

  private final Map<String, ValidatedFieldConfig> queryRules;
  private final ValueObfuscate valueObfuscate;
  private final boolean enabled;
  private static final Pattern AMPERSAND = Pattern.compile("&");

  public QueryParamObfuscationFilter(ObfuscateConfig config, ValueObfuscate valueObfuscate) {
    this.queryRules = config.getQueryRules();
    this.valueObfuscate = valueObfuscate;
    this.enabled = config.getGlobalConfig().enable();
    log.info("Initialized Query Param Filter with {} rules", queryRules.size());
  }

  @Override
  public String filter(@Nonnull String query) {
    if (!enabled || query.isEmpty() || queryRules.isEmpty()) {
      return query;
    }

    try {
      StringBuilder result = new StringBuilder(query.length() + 32);
      String[] pairs = AMPERSAND.split(query, -1);
      int matchCount = 0;

      for (int i = 0; i < pairs.length; i++) {
        String pair = pairs[i];
        int eqIdx = pair.indexOf('=');

        if (eqIdx > 0) {
          String key = decode(pair.substring(0, eqIdx));
          String value = pair.substring(eqIdx + 1);

          ValidatedFieldConfig rule = queryRules.get(key.toLowerCase());
          if (rule != null) {
            matchCount++;
            if (log.isTraceEnabled()) {
              log.trace("Obfuscating Query Param: [{}]", key);
            }

            String decodedVal = decode(value);
            String maskedVal = valueObfuscate.obfuscate(decodedVal, rule);
            result.append(encode(key)).append('=').append(encode(maskedVal));
          } else {
            result.append(pair);
          }
        } else {
          result.append(pair);
        }

        if (i < pairs.length - 1) {
          result.append('&');
        }
      }

      if (matchCount > 0 && log.isDebugEnabled()) {
        log.debug("Obfuscated {} query parameters", matchCount);
      }

      return result.toString();

    } catch (Exception e) {
      log.warn("Query param obfuscation failed. Returning original query. Error: {}", e.getMessage());
      return query;
    }
  }

  private String decode(String s) {
    try {
      return URLDecoder.decode(s, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.debug("URL Decode failed for string snippet: {}", s); // Debug 级别，防止刷屏
      return s;
    }
  }

  private String encode(String s) {
    try {
      return URLEncoder.encode(s, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.warn("URL Encode failed for value", e);
      return s;
    }
  }
}
