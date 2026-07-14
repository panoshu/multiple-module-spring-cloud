package com.example.core.domain.service.registry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 策略注册器泛型基类
 * 负责统一收集、存储、防重校验、查找策略
 *
 * @param <T> 策略接口类型
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 16:45
 */
public abstract class AbstractStrategyRegistry<T> {

  private final Map<String, T> strategyMap = new ConcurrentHashMap<>();
  private final Function<T, String> nameExtractor;

  /**
   * @param strategies    Spring 自动注入的策略实现列表
   * @param nameExtractor 从策略实例中提取名称的函数 (如 StepActionHandler::handlerName)
   */
  protected AbstractStrategyRegistry(List<T> strategies, Function<T, String> nameExtractor) {
    this.nameExtractor = nameExtractor;
    strategies.forEach(this::register);
  }

  /**
   * 注册并校验同名策略
   */
  private void register(T strategy) {
    String name = nameExtractor.apply(strategy);
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalStateException(String.format(
        "策略 [%s] 的名称不能为空！", strategy.getClass().getName()
      ));
    }

    // 同类型策略不允许同名
    T existing = strategyMap.putIfAbsent(name, strategy);
    if (existing != null) {
      throw new IllegalStateException(String.format(
        "发现同名策略冲突！策略类型: [%s], 名称: [%s], 已存在于 [%s], 冲突于 [%s]",
        strategy.getClass().getInterfaces()[0].getSimpleName(),
        name,
        existing.getClass().getName(),
        strategy.getClass().getName()
      ));
    }
  }

  /**
   * 根据名称获取策略实例
   */
  public T get(String name) {
    T strategy = strategyMap.get(name);
    if (strategy == null) {
      throw new IllegalArgumentException(String.format(
        "未找到名称为 [%s] 的策略实例", name
      ));
    }
    return strategy;
  }
}
