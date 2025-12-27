package com.example.shared.core.infrastructure;

import com.example.shared.core.api.IResultCode;
import com.example.shared.core.exception.BaseException;
import com.example.shared.core.exception.BusinessException;
import com.example.shared.core.exception.SystemException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 外部服务调用模板引擎
 * 核心职责：标准化外部调用的执行流程（构建 -> 调用 -> 异常处理 -> 断言 -> 转换）
 */
@Slf4j
public class ExternalCallTemplate {

  /**
   * 执行外部调用
   */
  public <REQ, RESP, T> T execute(ExternalCallBuilder<REQ, RESP, T> builder) {
    // 校验必填项
    builder.validate();

    String action = builder.actionName;
    Object bizId = builder.bizId;

    // 1. 准备请求
    REQ request = builder.requestFactory.get();

    RESP response;
    try {
      // 2. 执行远程调用 (捕获网络/框架层异常)
      response = builder.remoteCaller.apply(request);
    } catch (Exception e) {
      // 【优化】：优先处理降级
      if (builder.fallbackHandler != null) {
        log.warn("[{}] 接口调用异常触发降级, bizId={}, error={}", action, bizId, e.getMessage());
        return builder.fallbackHandler.apply(e);
      }

      // 【优化】：如果是业务异常，直接抛出，避免被 SystemException 再次包装
      if (e instanceof BaseException baseEx) {
        throw baseEx;
      }

      // 默认系统异常包装
      SystemException sysEx = new SystemException(builder.systemErrorCode, e);
      if (builder.systemFailureArgsExtractor != null) {
        sysEx.withDetail(builder.systemFailurePattern, builder.systemFailureArgsExtractor.apply(request));
      } else {
        sysEx.withDetail("{} 接口调用异常, bizId={}, req={}", action, bizId, request);
      }
      throw sysEx;
    }

    // 3. 基础判空
    if (response == null) {
      if (builder.fallbackHandler != null) {
        return builder.fallbackHandler.apply(new SystemException(builder.systemErrorCode, "Response is null"));
      }
      throw new SystemException(builder.systemErrorCode).withDetail("{} 返回空, bizId={}", action, bizId);
    }

    // 4. 业务成功断言
    if (!builder.successPredicate.test(response)) {
      // 断言失败也尝试降级
      if (builder.fallbackHandler != null) {
        log.warn("[{}] 业务断言失败触发降级, bizId={}, resp={}", action, bizId, response);
        return builder.fallbackHandler.apply(new BusinessException(builder.systemErrorCode, "Business check failed"));
      }

      // 计算具体的业务错误码
      IResultCode errorCode = builder.bizErrorCodeMapper.apply(response);
      BusinessException bizEx = new BusinessException(errorCode);

      if (builder.failureArgsExtractor != null) {
        bizEx.withDetail(builder.failurePattern, builder.failureArgsExtractor.apply(response));
      } else {
        bizEx.withDetail("{} 业务失败, bizId={}, resp={}", action, bizId, response);
      }
      throw bizEx;
    }

    // 5. 结果转换
    return builder.resultMapper.apply(response);
  }

  /**
   * 静态工厂方法
   */
  public static <REQ, RESP, T> ExternalCallBuilder<REQ, RESP, T> builder() {
    return new ExternalCallBuilder<>();
  }

  // —————————— Builder 定义 ——————————

  public static class ExternalCallBuilder<REQ, RESP, T> {
    private String actionName = "ExternalCall";
    private Object bizId;

    // 异常配置
    private IResultCode systemErrorCode;
    private Function<RESP, IResultCode> bizErrorCodeMapper;
    private String systemFailurePattern;
    private Function<REQ, Object[]> systemFailureArgsExtractor;

    // 执行逻辑
    @Getter
    private Supplier<REQ> requestFactory;
    private Function<REQ, RESP> remoteCaller;
    private Predicate<RESP> successPredicate;
    private Function<RESP, T> resultMapper;

    // 日志配置
    private String failurePattern;
    private Function<RESP, Object[]> failureArgsExtractor;

    // 降级处理函数
    private Function<Throwable, T> fallbackHandler;

    // —————— Setters (Fluent API) ——————

    public ExternalCallBuilder<REQ, RESP, T> action(String name, Object bizId) {
      this.actionName = name;
      this.bizId = bizId;
      return this;
    }

    public ExternalCallBuilder<REQ, RESP, T> systemError(IResultCode code) {
      this.systemErrorCode = code;
      return this;
    }

    public ExternalCallBuilder<REQ, RESP, T> businessError(IResultCode code) {
      this.bizErrorCodeMapper = resp -> code;
      return this;
    }

    public ExternalCallBuilder<REQ, RESP, T> mapBusinessError(Function<RESP, IResultCode> mapper) {
      this.bizErrorCodeMapper = mapper;
      return this;
    }

    public ExternalCallBuilder<REQ, RESP, T> request(Supplier<REQ> factory) {
      this.requestFactory = factory;
      return this;
    }

    public ExternalCallBuilder<REQ, RESP, T> call(Function<REQ, RESP> caller) {
      this.remoteCaller = caller;
      return this;
    }

    public ExternalCallBuilder<REQ, RESP, T> checkSuccess(Predicate<RESP> predicate) {
      this.successPredicate = predicate;
      return this;
    }

    public ExternalCallBuilder<REQ, RESP, T> map(Function<RESP, T> mapper) {
      this.resultMapper = mapper;
      return this;
    }

    public ExternalCallBuilder<REQ, RESP, T> onSystemFailure(String pattern, Function<REQ, Object[]> extractor) {
      this.systemFailurePattern = pattern;
      this.systemFailureArgsExtractor = extractor;
      return this;
    }

    public ExternalCallBuilder<REQ, RESP, T> onBusinessFailure(String pattern, Function<RESP, Object[]> extractor) {
      this.failurePattern = pattern;
      this.failureArgsExtractor = extractor;
      return this;
    }

    public ExternalCallBuilder<REQ, RESP, T> fallback(Function<Throwable, T> fallbackHandler) {
      this.fallbackHandler = fallbackHandler;
      return this;
    }

    // —————— 【核心修复】Getters (供 AbstractGateway 增强使用) ——————

    void validate() {
      Assert.notNull(requestFactory, "Request factory must not be null");
      Assert.notNull(remoteCaller, "Remote caller must not be null");
      Assert.notNull(successPredicate, "Success predicate must not be null");
      Assert.notNull(resultMapper, "Result mapper must not be null");
      Assert.notNull(systemErrorCode, "System error code must not be null");
      Assert.notNull(bizErrorCodeMapper, "Business error code mapper must not be null");
    }
  }
}
