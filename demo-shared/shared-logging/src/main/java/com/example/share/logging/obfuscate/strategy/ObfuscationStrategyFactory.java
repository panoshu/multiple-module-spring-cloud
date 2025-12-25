package com.example.share.logging.obfuscate.strategy;

import com.example.share.logging.obfuscate.config.ObfuscationStrategyType;
import com.example.share.logging.obfuscate.config.param.StrategyParams;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ObfuscationStrategyFactory {

  private final Map<ObfuscationStrategyType, ObfuscationStrategy> strategies;

  public ObfuscationStrategyFactory(List<ObfuscationStrategy> strategyList) {
    this.strategies = registerStrategies(strategyList);
    log.debug("Registered {} obfuscation strategies", strategies.size());
  }

  private Map<ObfuscationStrategyType, ObfuscationStrategy> registerStrategies(List<ObfuscationStrategy> strategyList) {
    Map<ObfuscationStrategyType, ObfuscationStrategy> strategyMap = new HashMap<>();

    strategyList.forEach(strategy -> {
      ObfuscationStrategyType strategyType = strategy.getType();
      if (strategyMap.containsKey(strategyType)) {
        log.warn("Duplicate strategy implementation for type '{}'. Using first implementation.", strategyType);
      } else {
        strategyMap.put(strategyType, strategy);
        log.debug("Registered strategy: {} -> {}", strategyType, strategy.getClass().getSimpleName());
      }
    });

    validateRequiredStrategies(strategyMap);
    return Map.copyOf(strategyMap);
  }

  private void validateRequiredStrategies(Map<ObfuscationStrategyType, ObfuscationStrategy> strategyMap) {
    List<ObfuscationStrategyType> requiredStrategies =
      java.util.Arrays.stream(ObfuscationStrategyType.values()).toList();

    List<ObfuscationStrategyType> missingStrategies = requiredStrategies.stream()
      .filter(type -> !strategyMap.containsKey(type))
      .toList();

    if (!missingStrategies.isEmpty()) {
      throw new IllegalStateException(
        "Missing required obfuscation strategies: %s. Available strategies: %s"
          .formatted(missingStrategies, strategyMap.keySet()));
    }
  }

  public ObfuscationStrategy getStrategy(ObfuscationStrategyType strategyType) {
    ObfuscationStrategy strategy = strategies.get(strategyType);
    if (strategy == null) {
      log.warn("Unknown strategy type: {}. Falling back to FULL strategy.", strategyType);
      return strategies.get(ObfuscationStrategyType.FULL);
    }
    return strategy;
  }

  public String obfuscate(ObfuscationStrategyType strategyType, String value, StrategyParams params) {
    ObfuscationStrategy strategy = getStrategy(strategyType);
    return strategy.obfuscate(value, params);
  }

  public Map<ObfuscationStrategyType, ObfuscationStrategy> getAllStrategies() {
    return Map.copyOf(strategies);
  }
}
