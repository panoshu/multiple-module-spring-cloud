package com.example.share.logging.obfuscate.strategy;

import com.example.share.logging.obfuscate.config.ObfuscationStrategyType;
import com.example.share.logging.obfuscate.config.param.StrategyParams;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ObfuscationStrategyFactory {

  private final Map<ObfuscationStrategyType, ObfuscationStrategy> strategies;

  public ObfuscationStrategyFactory(List<ObfuscationStrategy> obfuscationStrategies) {
    this.strategies = registerStrategies(obfuscationStrategies);
    log.debug("Registered {} obfuscation strategies", strategies.size());
  }

  private Map<ObfuscationStrategyType, ObfuscationStrategy> registerStrategies(
    List<ObfuscationStrategy> obfuscationStrategies) {
    Map<ObfuscationStrategyType, ObfuscationStrategy> strategyMap = new HashMap<>();

    obfuscationStrategies.forEach(strategy -> {
      ObfuscationStrategyType strategyType = strategy.getType();
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

  private void validateRequiredStrategies(Map<ObfuscationStrategyType, ObfuscationStrategy> strategyMap) {
    Set<ObfuscationStrategyType> missingStrategies = Arrays.stream(ObfuscationStrategyType.values())
      .filter(strategyType -> !strategyMap.containsKey(strategyType))
      .collect(Collectors.toUnmodifiableSet());

    if (!missingStrategies.isEmpty()) {
      throw new IllegalStateException(
        "Missing required obfuscation strategies: %s. Available strategies: %s"
          .formatted(missingStrategies, strategyMap.keySet()));
    }
  }

  public ObfuscationStrategy getStrategy(ObfuscationStrategyType strategyType) {
    return Optional.ofNullable(strategies.get(strategyType))
      .orElseGet(() -> fallbackToDefaultStrategy(strategyType));
  }

  private ObfuscationStrategy fallbackToDefaultStrategy(ObfuscationStrategyType strategyType) {
    log.warn("Unknown strategy type: {}. Falling back to FULL strategy.", strategyType);
    return Optional.ofNullable(strategies.get(ObfuscationStrategyType.FULL))
      .orElseThrow(() -> new IllegalStateException(
        "Cannot fallback to FULL strategy - it's not registered"));
  }

  public String obfuscate(ObfuscationStrategyType strategyType, String value, StrategyParams params) {
    ObfuscationStrategy strategy = getStrategy(strategyType);
    return strategy.obfuscate(value, params);
  }

  public Map<ObfuscationStrategyType, ObfuscationStrategy> getAllStrategies() {
    return Map.copyOf(strategies);
  }
}
