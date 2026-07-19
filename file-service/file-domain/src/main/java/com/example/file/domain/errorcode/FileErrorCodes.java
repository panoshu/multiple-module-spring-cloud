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
  EXCEL_EXPORT_FAILED("FILE_EXCEL_EXPORT_FAILED", "Excel 模板填充失败"),

  // 文件元数据相关
  FILE_METADATA_NOT_FOUND("FILE_METADATA_NOT_FOUND", "文件元数据不存在"),
  FILE_ALREADY_UPLOADED("FILE_ALREADY_UPLOADED", "文件已上传，不能重复上传"),
  FILE_STATUS_INVALID("FILE_STATUS_INVALID", "文件状态不允许此操作"),
  FILE_EXPIRED("FILE_EXPIRED", "文件已过期"),

  // 存储后端相关
  FILE_STORAGE_FAILED("FILE_STORAGE_FAILED", "文件存储失败"),
  FILE_STORAGE_TARGET_NOT_FOUND("FILE_STORAGE_TARGET_NOT_FOUND", "存储目标不存在"),
  FILE_STORAGE_TARGET_TYPE_MISMATCH("FILE_STORAGE_TARGET_TYPE_MISMATCH", "存储目标类型不匹配"),
  FILE_STORAGE_CONFIG_INVALID("FILE_STORAGE_CONFIG_INVALID", "存储配置无效"),
  FILE_COPY_FAILED("FILE_COPY_FAILED", "文件复制失败"),
  FILE_MD5_MISMATCH("FILE_MD5_MISMATCH", "文件 MD5 校验失败"),

  // Download/Read 相关
  FILE_DOWNLOAD_FAILED("FILE_DOWNLOAD_FAILED", "文件下载失败"),
  FILE_STREAM_CLOSED("FILE_STREAM_CLOSED", "文件流已关闭");

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
