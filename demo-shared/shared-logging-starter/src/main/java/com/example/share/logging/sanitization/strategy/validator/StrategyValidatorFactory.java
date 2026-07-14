package com.example.share.logging.sanitization.strategy.validator;

import com.example.share.logging.sanitization.properties.SanitizationStrategyType;
import com.example.share.logging.sanitization.strategy.param.StrategyParams;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * StrategyValidatorFactory
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 16:35
 */
@Slf4j
public class StrategyValidatorFactory {

  private final Map<SanitizationStrategyType, StrategyValidator> validators;

  public StrategyValidatorFactory(@NonNull List<StrategyValidator> strategyValidators) {
    this.validators = this.registerValidators(strategyValidators);
    log.info("Registered {} obfuscation validators", validators.size());
  }

  private Map<SanitizationStrategyType, StrategyValidator> registerValidators(
    List<StrategyValidator> strategyValidators) {

    Map<SanitizationStrategyType, StrategyValidator> validatorMap = new HashMap<>();

    strategyValidators.forEach(validator -> {
      if (validator == null) {
        log.warn("Skipping null strategy validator");
        return;
      }

      SanitizationStrategyType strategyType = validator.getStrategyType();
      if (strategyType == null) {
        log.warn("Skipping validator with null strategy type: {}", validator.getClass().getSimpleName());
        return;
      }

      if (validatorMap.containsKey(strategyType)) {
        log.warn("Duplicate validator implementation for type '{}'. Using first implementation: {}. Duplicate implementation: {}",
          strategyType,
          validatorMap.get(strategyType).getClass().getSimpleName(),
          validator.getClass().getSimpleName());
      } else {
        validatorMap.put(strategyType, validator);
        log.debug("Registered validator: {} -> {}", strategyType, validator.getClass().getSimpleName());
      }
    });

    validateAllStrategiesCovered(validatorMap);
    return Map.copyOf(validatorMap);
  }

  private void validateAllStrategiesCovered(Map<SanitizationStrategyType, StrategyValidator> validatorMap) {
    Set<SanitizationStrategyType> missingStrategies = Arrays.stream(SanitizationStrategyType.values())
      .filter(strategyType -> !validatorMap.containsKey(strategyType))
      .collect(Collectors.toUnmodifiableSet());

    if (!missingStrategies.isEmpty()) {
      throw new IllegalStateException(
        "Missing validators for required strategies: %s. Available strategies: %s"
          .formatted(missingStrategies, validatorMap.keySet()));
    }
  }

  /**
   * 获取策略验证器
   */
  public StrategyValidator getValidator(SanitizationStrategyType strategyType) {
    return Optional.ofNullable(validators.get(strategyType))
      .orElseThrow(() -> new IllegalArgumentException(
        "No validator found for strategy type: " + strategyType));
  }

  /**
   * 验证参数
   */
  public StrategyParams validateParams(SanitizationStrategyType strategyType, Map<String, Object> params) {
    return getValidator(strategyType).validateAndConvert(params);
  }
}
