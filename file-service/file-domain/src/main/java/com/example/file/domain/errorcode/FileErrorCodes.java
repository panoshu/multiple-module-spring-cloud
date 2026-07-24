package com.example.file.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * file-service 模块错误码定义。
 * <p>
 * 错误码区间 {@code 31001-31099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>5 位纯数字，首位 3 表示业务服务模块，2-3 位 10 表示 file-service</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 * <p>
 * 码段内部分组：
 * <ul>
 *   <li>31001-31009：模板与解析相关</li>
 *   <li>31011-31019：文件元数据相关</li>
 *   <li>31021-31029：存储后端相关</li>
 *   <li>31031-31039：下载与读取相关</li>
 *   <li>31041-31059：Token 访问相关</li>
 * </ul>
 *
 * @author file-service
 * @since 2026/5/12
 */
public enum FileErrorCodes implements ErrorDefinition {
  // ==================== 模板与解析相关（31001-31009） ====================
  CONFIG_NOT_FOUND("31001", "模板配置不存在"),
  CONFIG_INVALID("31002", "模板配置无效"),
  PARSE_FAILED("31003", "Excel 解析失败"),
  SUB_TASK_NOT_FOUND("31004", "子任务不存在"),
  SUB_TASK_EXPIRED("31005", "子任务已过期"),
  SUB_TASK_INVALID("31006", "子任务校验失败"),
  IDENTIFY_FAILED("31007", "无法识别源模板"),
  EXPRESSION_ERROR("31008", "表达式求值失败"),
  EXCEL_EXPORT_FAILED("31009", "Excel 模板填充失败"),

  // ==================== 文件元数据相关（31011-31019） ====================
  FILE_METADATA_NOT_FOUND("31011", "文件元数据不存在"),
  FILE_ALREADY_UPLOADED("31012", "文件已上传，不能重复上传"),
  FILE_STATUS_INVALID("31013", "文件状态不允许此操作"),
  FILE_EXPIRED("31014", "文件已过期"),

  // ==================== 存储后端相关（31021-31029） ====================
  FILE_STORAGE_FAILED("31021", "文件存储失败"),
  FILE_STORAGE_TARGET_NOT_FOUND("31022", "存储目标不存在"),
  FILE_STORAGE_TARGET_TYPE_MISMATCH("31023", "存储目标类型不匹配"),
  FILE_STORAGE_CONFIG_INVALID("31024", "存储配置无效"),
  FILE_COPY_FAILED("31025", "文件复制失败"),
  FILE_MD5_MISMATCH("31026", "文件 MD5 校验失败"),

  // ==================== 下载与读取相关（31031-31039） ====================
  FILE_DOWNLOAD_FAILED("31031", "文件下载失败"),
  FILE_STREAM_CLOSED("31032", "文件流已关闭"),

  // ==================== Token 访问相关（31041-31059） ====================
  FILE_TOKEN_INVALID("31041", "文件访问 token 无效或已过期"),
  FILE_TOKEN_EXPIRED("31042", "文件访问 token 已过期"),
  FILE_TOKEN_ALREADY_USED("31043", "文件访问 token 已被使用"),
  FILE_TOKEN_MISMATCH("31044", "文件访问 token 与当前用户不匹配"),
  FILE_CONTENT_TYPE_NOT_ALLOWED("31045", "文件类型不被允许"),
  FILE_SIZE_EXCEEDED("31046", "文件大小超出限制"),
  FILE_NOT_UPLOADABLE("31047", "文件当前状态不允许上传"),
  FILE_NOT_DOWNLOADABLE("31048", "文件当前状态不允许下载"),
  FILE_DIGEST_MISMATCH("31049", "文件摘要校验失败"),
  FILE_TOKEN_SECRET_NOT_CONFIGURED("31051", "文件 token 密钥未配置"),
  FILE_SESSION_HEADER_MISSING("31052", "会话 Header 缺失");

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
