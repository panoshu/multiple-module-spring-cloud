package com.example.core.api.registrar;

import java.util.Set;

/**
 * 业务类型注册器
 *
 * <p>业务服务通过实现本接口声明本服务支持的 BusinessType,由 {@code SupportedBusinessTypeValidator}
 * 在 Controller 入口校验,防止本服务被请求到不归自己处理的业务类型。
 *
 * <p>使用示例:
 * <pre>{@code
 * @Bean
 * public BusinessTypeRegistrar annuityTypeRegistrar() {
 *     return BusinessTypeRegistrar.of("ANNUITY_OPEN", "ANNUITY_CHANGE");
 * }
 * }</pre>
 *
 * @author panoshu
 */
public interface BusinessTypeRegistrar {

  /**
   * 工厂方法,创建一个包含指定业务类型的注册器。
   */
  static BusinessTypeRegistrar of(String... types) {
    Set<String> set = Set.of(types);
    return () -> set;
  }

  /**
   * 返回本服务支持的业务类型枚举名称集合。
   */
  Set<String> supportedBusinessTypes();
}
