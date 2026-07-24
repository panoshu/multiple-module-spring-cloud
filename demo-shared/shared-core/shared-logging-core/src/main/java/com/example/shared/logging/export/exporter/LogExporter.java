package com.example.shared.logging.export.exporter;

import com.example.shared.logging.core.model.HttpExchangeLog;

public interface LogExporter {
  void exportRequest(HttpExchangeLog httpExchangeLog);

  void exportResponse(HttpExchangeLog httpExchangeLog);
}
