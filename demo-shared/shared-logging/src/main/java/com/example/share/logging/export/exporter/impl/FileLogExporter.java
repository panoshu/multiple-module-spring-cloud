package com.example.share.logging.export.exporter.impl;

import com.example.share.logging.export.exporter.LogExporter;
import com.example.share.logging.core.model.HttpExchangeLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class FileLogExporter implements LogExporter {

  private final ObjectMapper objectMapper;

  @Override
  public void exportRequest(HttpExchangeLog httpExchangeLog) {
    try {
      log.info(objectMapper.writeValueAsString(httpExchangeLog));
    } catch (Exception e) {
      log.error("File Write Request failed", e);
    }
  }

  @Override
  public void exportResponse(HttpExchangeLog httpExchangeLog) {
    try {
      log.info(objectMapper.writeValueAsString(httpExchangeLog));
    } catch (Exception e) {
      log.error("File Write Response failed", e);
    }
  }
}
