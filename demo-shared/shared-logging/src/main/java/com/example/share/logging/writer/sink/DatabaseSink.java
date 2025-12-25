package com.example.share.logging.writer.sink;

import com.example.share.logging.writer.entity.HttpExchangeLog;
import com.example.share.logging.writer.mapper.HttpExchangeLogMapper;
import com.example.share.logging.writer.repository.HttpExchangeLogRepository;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.zalando.logbook.*;

@Slf4j
@RequiredArgsConstructor
public class DatabaseSink implements Sink {

  private final HttpExchangeLogRepository logRepository;
  private final HttpExchangeLogMapper httpExchangeLogMapper;

  @Override
  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void write(@Nonnull Precorrelation precorrelation, @Nonnull HttpRequest httpRequest) {
    HttpExchangeLog requestLog = httpExchangeLogMapper.toRequestLog(precorrelation, httpRequest);
    logRepository.upsertRequest(requestLog);
  }

  @Override
  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void write(@Nonnull Correlation correlation, @Nonnull HttpRequest request, @Nonnull HttpResponse response) {

    HttpExchangeLog responseLog = httpExchangeLogMapper.toResponseLog(correlation,request, response);
    logRepository.upsertResponse(responseLog);
  }
}
