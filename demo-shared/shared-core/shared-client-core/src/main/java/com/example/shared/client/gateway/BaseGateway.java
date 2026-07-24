package com.example.shared.client.gateway;

import com.example.shared.client.contract.ExternalResult;
import com.example.shared.exception.CommonError;
import com.example.shared.exception.SystemException;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public abstract class BaseGateway {

  // —————————— 1. Result<T> 包装器模式 (如银行) ——————————

  /**
   * 基础版：仅解包 (Unwrap)
   */
  protected <T> T perform(Supplier<ExternalResult<T>> apiCall) {
    // 复用下面的逻辑，传入 identity 原样返回
    return perform(apiCall, Function.identity());
  }

  /**
   * 进阶版：解包 + 转换 (Unwrap + Map)
   * 场景：银行返回 UserDTO，你想直接转成 UserDomain
   *
   * @param apiCall API 调用
   * @param mapper  转换函数 (T -> R)
   * @param <T>     外部 DTO 类型
   * @param <R>     内部 Domain 类型
   */
  protected <T, R> R perform(Supplier<ExternalResult<T>> apiCall, Function<T, R> mapper) {
    // 1. 执行与验错
    ExternalResult<T> result = apiCall.get();

    // 2. 提取数据
    T data = this.handleResult(result);

    // 3. 转换 (防御性判空：如果 data 是 null，直接返回 null，不调 converter)
    return (data != null) ? mapper.apply(data) : null;
  }

  // —————————— 2. 直连模式 (如 REST API) ——————————

  /**
   * 基础版：直连 (Direct)
   */
  protected <T> T performDirect(Supplier<T> apiCall) {
    return performDirect(apiCall, Function.identity());
  }

  /**
   * 进阶版：直连 + 转换 (Direct + Map)
   * 场景：GitHub 返回 UserJson，你想直接转成 UserVO
   */
  protected <T, R> R performDirect(Supplier<T> apiCall, Function<T, R> mapper) {
    // 1. 执行
    T result = apiCall.get();

    // 2. 转换
    return (result != null) ? mapper.apply(result) : null;
  }

  // —————————— 3. Object 原始模式 ——————————

  /**
   * 执行返回 Object 的调用，自行处理类型转换
   * 用于 TradeQueryClient 返回 TradeRootResponse<Object> 的场景
   */
  protected Object performRaw(Supplier<ExternalResult<Object>> apiCall) {
    ExternalResult<Object> result = apiCall.get();
    return this.handleResult(result);
  }

  /**
   * 统一处理外部返回结果转换
   *
   * @param result 外部接口返回的包装对象
   * @param <T>    数据类型
   * @return 业务数据本体
   */
  private <T> T handleResult(ExternalResult<T> result) {
    if (result == null) {
      throw new SystemException(CommonError.REMOTE_SERVICE_ERROR).withLogDetail("外部服务返回空响应");
    }

    if (!result.isSuccess()) {
      log.error("BaseGateway 外部接口业务失败: [{}]{}", result.getErrorCode(), result.getErrorMsg());
      throw new SystemException(CommonError.REMOTE_SERVICE_ERROR)
        .withLogDetail("BaseGateway 外部调用异常 [%s]: %s".formatted(result.getErrorCode(), result.getErrorMsg()));
    }

    return result.getData();
  }

}
