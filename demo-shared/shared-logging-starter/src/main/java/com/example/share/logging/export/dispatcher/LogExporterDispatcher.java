package com.example.share.logging.export.dispatcher;

import com.example.share.logging.core.model.HttpExchangeLog;
import com.example.share.logging.export.exporter.LogExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class LogExporterDispatcher implements LogExporter {

  // 注入具体的 Exporter (DB, File)
  private final List<LogExporter> exporters;

  @Override
  public void exportRequest(HttpExchangeLog httpExchangeLog) {
    for (LogExporter exporter : exporters) {
      log.debug(exporter.getClass().getSimpleName());
      exporter.exportRequest(httpExchangeLog);
    }
  }

  @Override
  public void exportResponse(HttpExchangeLog httpExchangeLog) {
    for (LogExporter exporter : exporters) {
      exporter.exportResponse(httpExchangeLog);
    }
  }
}
