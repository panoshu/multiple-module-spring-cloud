package com.example.shared.web.handler;

import com.example.shared.exception.CommonError;
import com.example.shared.web.core.api.ApiResult;
import com.github.lianjiatech.retrofit.spring.boot.exception.RetrofitIOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * IntegrationExceptionHandler
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/2/9 21:57
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@Slf4j
public class RetrofitIOExceptionHandler {

  /**
   * 专门处理 RetrofitIOException
   * 场景：只有 integration 服务依赖了 retrofit，这个异常是该服务特有的
   */
  @ExceptionHandler(RetrofitIOException.class)
  public ApiResult<Void> handleRetrofitException(RetrofitIOException e) {
    log.error("Integration External Call Failed: ", e);

    return ApiResult.failure(CommonError.NETWORK_ERROR.getCode(), CommonError.NETWORK_ERROR.getMessage());
  }

}
