package com.example.share.logging.integration.logbook;

import com.example.share.logging.core.api.LogProcessor;
import com.example.share.logging.core.model.HttpExchangeLog;
import lombok.RequiredArgsConstructor;
import org.zalando.logbook.*;

import javax.annotation.Nonnull;

/**
 * LogbookSinkAdapter
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/27 14:38
 */
@RequiredArgsConstructor
public class LogbookSinkAdapter implements Sink { // 实现 Logbook 契约

  private final LogbookMapper mapper;       // 转换器
  private final LogProcessor logProcessor;  // 核心业务接口

  @Override
  public void write(@Nonnull Precorrelation precorrelation, @Nonnull HttpRequest request) {
    // 1. 转换
    HttpExchangeLog log = mapper.toRequestLog(precorrelation, request);
    // 2. 调用核心业务的“请求处理”方法
    logProcessor.processRequest(log);
  }

  @Override
  public void write(@Nonnull Correlation correlation, @Nonnull HttpRequest request, @Nonnull HttpResponse response) {
    // 1. 转换
    HttpExchangeLog log = mapper.toResponseLog(correlation, request, response);
    // 2. 调用核心业务的“响应处理”方法
    logProcessor.processResponse(log);
  }
}
