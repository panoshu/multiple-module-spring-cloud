package com.example.share.logging.core.api;

import com.example.share.logging.core.model.HttpExchangeLog;

/**
 * LogProcessor (日志处理器)
 * 定义系统处理日志的核心能力（Input Port）
 */
public interface LogProcessor {

  // 处理请求阶段日志
  void processRequest(HttpExchangeLog requestLog);

  // 处理响应阶段日志
  void processResponse(HttpExchangeLog responseLog);
}
