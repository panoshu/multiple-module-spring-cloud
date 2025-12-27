 package com.example.share.logging.export.dispatcher;

import com.example.share.logging.core.model.HttpExchangeLog;
import com.example.share.logging.export.exporter.LogExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary // 让 Pipeline 注入这个主要的 Exporter
@RequiredArgsConstructor
public class LogExporterDispatcher implements LogExporter {

  // 注入具体的 Exporter (DB, File)
  private final List<LogExporter> exporters;

  @Override
  public void exportRequest(HttpExchangeLog log) {
    for (LogExporter exporter : exporters) {
      exporter.exportRequest(log);
    }
  }

  @Override
  public void exportResponse(HttpExchangeLog log) {
    for (LogExporter exporter : exporters) {
      exporter.exportResponse(log);
    }
  }
}
