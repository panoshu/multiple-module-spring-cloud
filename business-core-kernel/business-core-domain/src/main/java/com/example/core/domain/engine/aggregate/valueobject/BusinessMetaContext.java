package com.example.core.domain.engine.aggregate.valueobject;

import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.types.OperatingMode;
import com.example.core.types.CustomerNo;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;

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
  OperatingMode operationModel,
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

  /** 外部协作幂等键在扩展事实中的保留键（spec §7.3 / §11.4）。 */
  public static final String REQUEST_NO_FACT = "kernel.requestNo";

  /** 事务外派发载荷在扩展事实中的保留键（spec §9.2 阶段 B / §16.4 检查点）。 */
  public static final String DISPATCH_PAYLOAD_FACT = "kernel.dispatchPayload";

  /** 读取编排器注入的外部协作幂等键；未注入时返回 null（可空过渡）。 */
  public String requestNo() {
    Object value = extensionFacts == null ? null : extensionFacts.get(REQUEST_NO_FACT);
    return value == null ? null : String.valueOf(value);
  }

  /** 读取挂起 Handler 登记的事务外派发载荷；未登记时返回 null。 */
  public String dispatchPayload() {
    Object value = extensionFacts == null ? null : extensionFacts.get(DISPATCH_PAYLOAD_FACT);
    return value == null ? null : String.valueOf(value);
  }

  /** 挂起 Handler 登记事务外派发载荷（由编排器写入 StepExecution 检查点）。 */
  public void registerDispatchPayload(String payload) {
    extensionFacts.put(DISPATCH_PAYLOAD_FACT, payload);
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
