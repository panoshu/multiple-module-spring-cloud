package com.example.auth.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式行级数据过滤注解，由 shared-permission-starter 的 DataScopeAspect 拦截。
 *
 * <p>使用示例：
 * <pre>{@code
 * @DataScope(business = "ANNUITY")
 * @RequirePermission(business = "ANNUITY", action = "VIEW")
 * public PageData<BatchStatusDTO> listBatches(ListBatchQuery query) { ... }
 * }</pre>
 *
 * @author auth-api
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

  /**
   * 业务编码，用于查询该用户在此业务下的可见范围。
   * 与 @RequirePermission 的 business 保持一致。
   */
  String business();

  /**
   * 过滤维度，决定 Repository 拼接哪个字段。
   * 默认 PLAN：大多数业务表都包含 plan_no。
   */
  DataScopeDimension dimension() default DataScopeDimension.PLAN;
}
