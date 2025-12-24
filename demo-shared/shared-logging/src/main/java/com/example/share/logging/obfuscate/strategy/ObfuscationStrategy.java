package com.example.share.logging.obfuscate.strategy;

import com.example.share.logging.obfuscate.config.ObfuscationStrategyType;
import com.example.share.logging.obfuscate.config.param.StrategyParams;

/**
 * 脱敏策略接口，所有策略必须实现此接口
 */
public interface ObfuscationStrategy {

  /**
   * 获取策略类型
   */
  ObfuscationStrategyType getType();

  /**
   * 执行脱敏操作
   * @param value 原始值
   * @param params 策略参数
   * @return 脱敏后的值
   */
  String obfuscate(String value, StrategyParams params);

  /**
   * 检查策略是否支持该值
   */
  default boolean supports(String value) {
    return value != null && !value.isBlank();
  }
}
