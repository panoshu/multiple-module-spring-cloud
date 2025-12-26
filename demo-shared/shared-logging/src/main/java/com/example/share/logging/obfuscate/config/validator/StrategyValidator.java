package com.example.share.logging.obfuscate.config.validator;

import com.example.share.logging.obfuscate.config.ObfuscationStrategyType;
import com.example.share.logging.obfuscate.config.param.StrategyParams;

import java.util.Map;

/**
 * StrategyValidator
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 16:35
 */
public interface StrategyValidator {

  /**
   * 获取支持的策略类型
   */
  ObfuscationStrategyType getStrategyType();

  /**
   * 验证并转换参数, 需要自行处理参数为空的情况
   */
  StrategyParams validateAndConvert(Map<String, Object> params);

}
