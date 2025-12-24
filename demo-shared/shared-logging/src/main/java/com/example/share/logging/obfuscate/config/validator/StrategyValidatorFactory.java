package com.example.share.logging.obfuscate.config.validator;

import com.example.share.logging.obfuscate.config.ObfuscationStrategyType;
import com.example.share.logging.obfuscate.config.param.StrategyParams;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StrategyValidatorFactory
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 16:35
 */
@Component
public class StrategyValidatorFactory {

  private final Map<ObfuscationStrategyType, StrategyValidator> validatorMap;

  public StrategyValidatorFactory(ListableBeanFactory beanFactory) {
    this.validatorMap = new ConcurrentHashMap<>();

    // 自动收集所有 StrategyValidator 实现
    Map<String, StrategyValidator> validators =
      beanFactory.getBeansOfType(StrategyValidator.class);

    validators.values().forEach(validator -> validatorMap.put(validator.getStrategyType(), validator));

    // 验证是否所有策略都有对应的验证器
    for (ObfuscationStrategyType strategyType : ObfuscationStrategyType.values()) {
      if (!validatorMap.containsKey(strategyType)) {
        throw new IllegalStateException(
          "No validator found for strategy: " + strategyType);
      }
    }
  }

  /**
   * 获取策略验证器
   */
  public StrategyValidator getValidator(ObfuscationStrategyType strategyType) {
    StrategyValidator validator = validatorMap.get(strategyType);
    if (validator == null) {
      throw new IllegalArgumentException(
        "No validator registered for strategy type: " + strategyType);
    }
    return validator;
  }

  /**
   * 验证参数
   */
  public StrategyParams validateParams(ObfuscationStrategyType strategyType,
                                       Map<String, Object> params) {
    StrategyValidator validator = getValidator(strategyType);
    return validator.validateAndConvert(params);
  }
}
