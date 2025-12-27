package com.example.share.logging.export.exporter;

import com.example.share.logging.core.model.HttpExchangeLog;

public interface LogExporter {
  void exportRequest(HttpExchangeLog httpExchangeLog);
  void exportResponse(HttpExchangeLog httpExchangeLog);
}
