package com.example.share.logging.writer.sink;

import com.example.share.logging.writer.entity.HttpExchangeLog;
import com.example.share.logging.writer.mapper.HttpExchangeLogMapper;
import com.example.share.logging.writer.repository.HttpExchangeLogPGRepository;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zalando.logbook.*;

import java.util.concurrent.Executor;

@Slf4j
@RequiredArgsConstructor
public class DatabaseSink implements Sink {

  private final HttpExchangeLogPGRepository logRepository;
  private final HttpExchangeLogMapper httpExchangeLogMapper;
  private final Executor taskExecutor;

  @Override
  public void write(@Nonnull Precorrelation precorrelation, @Nonnull HttpRequest request) {
    // 1. 同步：主线程提取数据
    HttpExchangeLog requestLog = httpExchangeLogMapper.toRequestLog(precorrelation, request);

    // 2. 异步：调用通用方法
    executeAsync(() -> logRepository.upsertRequest(requestLog), "Failed to write request log async");
  }

  @Override
  public void write(@Nonnull Correlation correlation, @Nonnull HttpRequest request, @Nonnull HttpResponse response) {
    // 1. 同步：主线程提取数据（关键！防止 Response 被回收）
    HttpExchangeLog responseLog = httpExchangeLogMapper.toResponseLog(correlation, request, response);

    // 2. 异步：调用通用方法
    executeAsync(() -> logRepository.upsertResponse(responseLog), "Failed to write response log async");
  }

  /**
   * 通用异步执行包装器
   *
   * @param runnable 具体要执行的数据库操作
   * @param errorMsg 操作失败时的日志提示语
   */
  private void executeAsync(Runnable runnable, String errorMsg) {
    taskExecutor.execute(() -> {
      try {
        log.info(">>> [Async DB-Sink] Thread: {} | IsVirtual: {}",
          Thread.currentThread(),
          Thread.currentThread().isVirtual());
        runnable.run();
      } catch (Exception e) {
        log.error(errorMsg, e);
      }
    });
  }
}
