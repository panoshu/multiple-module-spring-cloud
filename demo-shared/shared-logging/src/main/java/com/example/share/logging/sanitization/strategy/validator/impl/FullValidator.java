package com.example.share.logging.sanitization.strategy.validator.impl;

import com.example.share.logging.sanitization.properties.SanitizationStrategyType;
import com.example.share.logging.sanitization.strategy.param.FullParams;
import com.example.share.logging.sanitization.strategy.validator.StrategyValidator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * FullValidator
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 17:51
 */
@Component
public class FullValidator implements StrategyValidator {

  @Override
  public SanitizationStrategyType getStrategyType() {
    return SanitizationStrategyType.FULL;
  }

  @Override
  public FullParams validateAndConvert(Map<String, Object> params) {
    return new FullParams();
  }
}
