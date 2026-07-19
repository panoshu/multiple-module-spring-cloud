package com.example.file.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

public enum FileErrorCodes implements ErrorDefinition {
  CONFIG_NOT_FOUND("FILE_CONFIG_NOT_FOUND", "模板配置不存在"),
  CONFIG_INVALID("FILE_CONFIG_INVALID", "模板配置无效"),
  PARSE_FAILED("FILE_PARSE_FAILED", "Excel 解析失败"),
  SUB_TASK_NOT_FOUND("FILE_SUB_TASK_NOT_FOUND", "子任务不存在"),
  SUB_TASK_EXPIRED("FILE_SUB_TASK_EXPIRED", "子任务已过期"),
  SUB_TASK_INVALID("FILE_SUB_TASK_INVALID", "子任务校验失败"),
  IDENTIFY_FAILED("FILE_IDENTIFY_FAILED", "无法识别源模板"),
  EXPRESSION_ERROR("FILE_EXPRESSION_ERROR", "表达式求值失败"),
  EXCEL_EXPORT_FAILED("FILE_EXCEL_EXPORT_FAILED", "Excel 模板填充失败");

  private final String code;
  private final String message;

  FileErrorCodes(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String code() { return code; }

  @Override
  public String message() { return message; }
}
