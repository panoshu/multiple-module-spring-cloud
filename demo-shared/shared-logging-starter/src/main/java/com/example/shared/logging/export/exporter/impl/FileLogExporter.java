package com.example.shared.logging.export.exporter.impl;

import com.example.shared.logging.core.model.HttpExchangeLog;
import com.example.shared.logging.export.exporter.LogExporter;
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
