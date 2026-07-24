package com.example.shared.web.trace.spi;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 业务上下文访问器
 * <p>
 * 职责：
 * 1. 管理业务 ID 的作用域生命周期 (开启/关闭 Scope)
 * 2. 支持跨线程/跨服务透传
 */
public interface BizContextAccessor {

  /**
   * 在指定的业务上下文 Header 中执行任务 (支持抛出受检异常)
   * <p>
   * 场景：用于 Aspect 切面，因为 joinPoint.proceed() 会抛出 Throwable
   *
   * @param contextMap 别名映射后的 Header Key -> Value 集合
   * @param action     需要执行的业务逻辑
   * @return 业务逻辑的返回值
   * @throws Throwable 业务逻辑抛出的原始异常
   */
  <T> T withContext(Map<String, String> contextMap, ThrowableSupplier<T> action) throws Throwable;

  /**
   * 在指定的业务上下文 Header 中执行任务 (仅支持 RuntimeException)
   * <p>
   * 场景：用于 Filter 或普通业务代码
   *
   * @param contextMap 别名映射后的 Header Key -> Value 集合
   * @param action     需要执行的业务逻辑
   * @return 业务逻辑的返回值
   */
  default <T> T withContext(Map<String, String> contextMap, Supplier<T> action) {
    try {
      return withContext(contextMap, (ThrowableSupplier<T>) action::get);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 针对 void 方法的便捷重载
   */
  default void withContext(Map<String, String> contextMap, Runnable action) {
    // 通过显式强转 (Supplier<Void>) 消除歧义，明确调用 default <T> T withContext(...)
    withContext(contextMap, (Supplier<Void>) () -> {
      action.run();
      return null;
    });
  }
}
