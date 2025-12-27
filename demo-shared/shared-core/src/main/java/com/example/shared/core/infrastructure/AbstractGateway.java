package com.example.shared.core.infrastructure;

import com.example.shared.core.api.IResultCode;
import com.example.shared.core.model.BaseExternalRequest;
import com.example.shared.core.trace.context.BizContext; // 确保引用了之前定义的 BizContext
import org.springframework.beans.factory.annotation.Autowired;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 网关适配器基类
 * <p>
 * 泛型说明：
 * REQ: 请求体类型。不强制继承 BaseExternalRequest，以兼容 Void 或 SDK 对象。
 * RESP: 响应体类型。
 * </p>
 */
public abstract class AbstractGateway<REQ, RESP> {

  @Autowired
  protected ExternalCallTemplate template;

  // —————— 抽象契约 ——————
  protected abstract boolean isSuccess(RESP response);
  protected abstract IResultCode getSystemError();
  protected abstract IResultCode getDefaultBusinessError();

  protected String getDefaultErrorPattern() { return "外部接口异常: {}"; }
  protected Object[] extractDefaultErrorArgs(RESP response) { return new Object[]{response}; }

  // —————— 核心执行入口 ——————

  /**
   * 执行由 Builder 构建的请求
   * [功能]: 在这里进行“运行时增强”，自动注入 Header
   */
  protected <T> T execute(ExternalCallTemplate.ExternalCallBuilder<REQ, RESP, T> builder) {
    enhanceRequestFactory(builder);
    return template.execute(builder);
  }

  /**
   * 执行由 Void Builder 构建的请求
   * [功能]: 无参请求无需增强，直接执行
   */
  protected <T> T executeVoid(ExternalCallTemplate.ExternalCallBuilder<Void, RESP, T> builder) {
    return template.execute(builder);
  }

  // —————— 风格统一的 API ——————

  /**
   * [Perform]: 快速执行 (无降级)
   */
  protected <T> T perform(String action, Object bizId,
                          Supplier<REQ> reqSupplier,
                          Function<REQ, RESP> remoteCall,
                          Function<RESP, T> mapper) {
    return perform(action, bizId, reqSupplier, remoteCall, mapper, null);
  }

  /**
   * [Perform]: 快速执行 (带降级)
   */
  protected <T> T perform(String action, Object bizId,
                          Supplier<REQ> reqSupplier,
                          Function<REQ, RESP> remoteCall,
                          Function<RESP, T> mapper,
                          Function<Throwable, T> fallback) {
    var builder = this.<T>build(action, bizId)
      .request(reqSupplier)
      .call(remoteCall)
      .map(mapper);

    if (fallback != null) {
      builder.fallback(fallback);
    }
    return execute(builder);
  }

  /**
   * [Build]: 构建标准请求
   */
  protected <T> ExternalCallTemplate.ExternalCallBuilder<REQ, RESP, T> build(String action, Object bizId) {
    // 使用 new 关键字避免静态方法泛型推断歧义
    return new ExternalCallTemplate.ExternalCallBuilder<REQ, RESP, T>()
      .action(action, bizId)
      .systemError(getSystemError())
      .businessError(getDefaultBusinessError())
      .checkSuccess(this::isSuccess)
      .onBusinessFailure(getDefaultErrorPattern(), this::extractDefaultErrorArgs);
  }

  /**
   * [BuildVoid]: 构建无参请求 (REQ 固定为 Void)
   */
  protected <T> ExternalCallTemplate.ExternalCallBuilder<Void, RESP, T> buildVoid(String action, Object bizId) {
    // 【关键修复】直接 new，明确指定泛型 <Void, RESP, T>，彻底解决 "方法调用不明确" 报错
    return new ExternalCallTemplate.ExternalCallBuilder<Void, RESP, T>()
      .action(action, bizId)
      .systemError(getSystemError())
      .businessError(getDefaultBusinessError())
      .checkSuccess(this::isSuccess)
      .onBusinessFailure(getDefaultErrorPattern(), this::extractDefaultErrorArgs)
      .request(() -> null); // 默认填充 null 请求
  }

  // —————— 运行时增强逻辑 ——————

  /**
   * 动态拦截 Request 工厂，如果对象符合 BaseExternalRequest 契约，则注入通用 Header
   */
  private <T> void enhanceRequestFactory(ExternalCallTemplate.ExternalCallBuilder<REQ, RESP, T> builder) {
    Supplier<REQ> originalFactory = builder.getRequestFactory();
    if (originalFactory == null) return;

    // 替换为增强后的 Factory
    builder.request(() -> {
      // 1. 创建原始对象
      REQ req = originalFactory.get();

      // 2. 运行时探测：是否遵循了 BaseExternalRequest 契约
      // 使用通配符 <?> 因为我们只调用 addHeader，不关心其子类类型
      if (req instanceof BaseExternalRequest<?> baseReq) {
        // 3. 注入全链路 ID
        String traceId = BizContext.getBizId();
        if (traceId != null) {
          baseReq.addHeader("X-Trace-Id", traceId);
        }

        // 4. 注入其他上下文信息 (如 BatchId)
        String batchId = BizContext.getBatchId();
        if (batchId != null) {
          baseReq.addHeader("X-Batch-Id", batchId);
        }
      }
      return req;
    });
  }
}
