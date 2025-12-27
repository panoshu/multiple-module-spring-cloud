package com.example.share.logging.sanitization.strategy.validator.impl;

import com.example.share.logging.sanitization.properties.SanitizationStrategyType;
import com.example.share.logging.sanitization.strategy.param.KeepFirstLastParams;
import com.example.share.logging.sanitization.strategy.validator.StrategyValidator;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * KeepFirstLastValidator
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 17:50
 */
@Component
public class KeepFirstLastValidator implements StrategyValidator {

  private static final ConversionService CONVERSION_SERVICE =
    ApplicationConversionService.getSharedInstance();

  @Override
  public SanitizationStrategyType getStrategyType() {
    return SanitizationStrategyType.KEEP_FIRST_LAST;
  }

  @Override
  public KeepFirstLastParams validateAndConvert(Map<String, Object> params) {
    int showPrefix = getIntParam(params, "show-prefix", 1);
    int showSuffix = getIntParam(params, "show-suffix", 1);
    return new KeepFirstLastParams(showPrefix, showSuffix);
  }

  private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
    if (params == null || !params.containsKey(key)) {
      return defaultValue;
    }
    Object value = params.get(key);
    return convertToInt(value, key, defaultValue);
  }

  private int convertToInt(Object value, String key, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Integer) {
      return (Integer) value;
    }
    if (CONVERSION_SERVICE.canConvert(value.getClass(), Integer.class)) {
      return CONVERSION_SERVICE.convert(value, Integer.class);
    }
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
        String.format("Invalid %s value: %s", key, value), e);
    }
  }
}
