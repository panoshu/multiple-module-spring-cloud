package com.example.core.domain.engine.aggregate.valueobject;

import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置查询上下文
 * 各业务可以实现 BusinessFactExtractor 领域服务从自身的聚合根中提取扩展信息注入 extensionFacts
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 10:52
 */
public record BusinessMetaContext(
  CustomerNo customerNo,
  ProductNo productNo,
  OperationModel operationModel,
  PlanNo planNo,
  BusinessType businessType,
  AccountManager accountManager,
  Map<String, Object> extensionFacts
) {

  /**
   * 紧凑构造器:将 extensionFacts 归一化为可变且线程安全的 {@link ConcurrentHashMap}。
   * <p>
   * <b>【为何需要】</b>编排管道的 {@code applyContextMutations} 会向 extensionFacts 写入突变数据,
   * 同时异步扩展动作通过 {@code CompletableFuture} 并发读取同一上下文。原始 Map 既可能不可变
   * (如 {@code Collections.emptyMap()})导致写入抛异常,也可能在并发读写下产生可见性问题。
   * 统一包装为 ConcurrentHashMap 后,既保留 putAll 写入语义,又保证多线程安全。
   */
  public BusinessMetaContext {
    if (extensionFacts == null) {
      extensionFacts = new ConcurrentHashMap<>();
    } else if (!(extensionFacts instanceof ConcurrentHashMap)) {
      extensionFacts = new ConcurrentHashMap<>(extensionFacts);
    }
  }

  public static BusinessMetaContext of(BusinessContext businessContext) {
    return new BusinessMetaContext(
      businessContext.customerNo(), businessContext.productNo(), businessContext.operationModel(), businessContext.planNo(),
      businessContext.businessType(), businessContext.accountManager(), null);
  }

  public static BusinessMetaContext withExtensionFacts(BusinessMetaContext configQueryContext, Map<String, Object> extensionFacts) {
    return new BusinessMetaContext(
      configQueryContext.customerNo,
      configQueryContext.productNo,
      configQueryContext.operationModel,
      configQueryContext.planNo,
      configQueryContext.businessType,
      configQueryContext.accountManager,
      extensionFacts
    );
  }

}
