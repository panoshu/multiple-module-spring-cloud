package com.example.share.logging.export.exporter.impl;

import com.example.share.logging.export.exporter.LogExporter;
import com.example.share.logging.core.model.HttpExchangeLog;
import com.example.share.logging.export.persistence.repository.HttpExchangeLogPGRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DatabaseLogExporter implements LogExporter {

  private final HttpExchangeLogPGRepository repository;

  @Override
  public void exportRequest(HttpExchangeLog httpExchangeLog) {
    try {
      repository.upsertRequest(httpExchangeLog);
    } catch (Exception e) {
      log.error("DB Write Request failed", e);
    }
  }

  @Override
  public void exportResponse(HttpExchangeLog httpExchangeLog) {
    try {
      repository.upsertResponse(httpExchangeLog);
    } catch (Exception e) {
      log.error("DB Write Response failed", e);
    }
  }
}
