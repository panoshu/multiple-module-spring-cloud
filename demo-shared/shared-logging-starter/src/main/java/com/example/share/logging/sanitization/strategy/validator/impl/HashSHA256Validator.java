package com.example.share.logging.sanitization.strategy.validator.impl;

import com.example.share.logging.sanitization.properties.SanitizationStrategyType;
import com.example.share.logging.sanitization.strategy.param.HashSHA256Params;
import com.example.share.logging.sanitization.strategy.validator.StrategyValidator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * HashMd5Validator
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 17:51
 */
@Component
public class HashSHA256Validator implements StrategyValidator {

  @Override
  public SanitizationStrategyType getStrategyType() {
    return SanitizationStrategyType.HASH_SHA256;
  }

  @Override
  public HashSHA256Params validateAndConvert(Map<String, Object> params) {
    return new HashSHA256Params();
  }
}
