package com.example.share.logging.sanitization.strategy;

import com.example.share.logging.sanitization.properties.SanitizationStrategyType;
import com.example.share.logging.sanitization.strategy.param.StrategyParams;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SanitizationStrategyFactory {

  private final Map<SanitizationStrategyType, SanitizationStrategy> strategies;

  public SanitizationStrategyFactory(List<SanitizationStrategy> obfuscationStrategies) {
    this.strategies = registerStrategies(obfuscationStrategies);
    log.info("Registered {} obfuscation strategies", strategies.size());
  }

  private Map<SanitizationStrategyType, SanitizationStrategy> registerStrategies(
    List<SanitizationStrategy> obfuscationStrategies) {
    Map<SanitizationStrategyType, SanitizationStrategy> strategyMap = new HashMap<>();

    obfuscationStrategies.forEach(strategy -> {
      SanitizationStrategyType strategyType = strategy.getType();
      if (strategyMap.containsKey(strategyType)) {
        log.warn("Duplicate strategy implementation for type '{}'. Using first implementation: {}. Duplicate implementation: {}",
          strategyType, strategyMap.get(strategyType).getClass().getSimpleName(), strategy.getClass().getSimpleName());
      } else {
        strategyMap.put(strategyType, strategy);
        log.debug("Registered strategy: {} -> {}", strategyType, strategy.getClass().getSimpleName());
      }
    });

    validateRequiredStrategies(strategyMap);
    return Map.copyOf(strategyMap);
  }

  private void validateRequiredStrategies(Map<SanitizationStrategyType, SanitizationStrategy> strategyMap) {
    Set<SanitizationStrategyType> missingStrategies = Arrays.stream(SanitizationStrategyType.values())
      .filter(strategyType -> !strategyMap.containsKey(strategyType))
      .collect(Collectors.toUnmodifiableSet());

    if (!missingStrategies.isEmpty()) {
      throw new IllegalStateException(
        "Missing required obfuscation strategies: %s. Available strategies: %s"
          .formatted(missingStrategies, strategyMap.keySet()));
    }
  }

  public SanitizationStrategy getStrategy(SanitizationStrategyType strategyType) {
    return Optional.ofNullable(strategies.get(strategyType))
      .orElseGet(() -> fallbackToDefaultStrategy(strategyType));
  }

  private SanitizationStrategy fallbackToDefaultStrategy(SanitizationStrategyType strategyType) {
    log.warn("Unknown strategy type: {}. Falling back to FULL strategy.", strategyType);
    return Optional.ofNullable(strategies.get(SanitizationStrategyType.FULL))
      .orElseThrow(() -> new IllegalStateException(
        "Cannot fallback to FULL strategy - it's not registered"));
  }

  public String sanitize(SanitizationStrategyType strategyType, String value, StrategyParams params) {
    SanitizationStrategy strategy = getStrategy(strategyType);
    return strategy.sanitize(value, params);
  }

  public Map<SanitizationStrategyType, SanitizationStrategy> getAllStrategies() {
    return Map.copyOf(strategies);
  }
}
