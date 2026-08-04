package com.example.file.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * file-service 模块错误码定义。
 * <p>
 * 错误码区间 {@code SERVICE.FILE.0001-SERVICE.FILE.0099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式：SERVICE.FILE.XXXX（业务服务模块 - file-service）</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 * <p>
 * 码段内部分组：
 * <ul>
 *   <li>SERVICE.FILE.0001-0009：模板与解析相关</li>
 *   <li>SERVICE.FILE.0010-0013：文件元数据相关</li>
 *   <li>SERVICE.FILE.0014-0019：存储后端相关</li>
 *   <li>SERVICE.FILE.0020-0021：下载与读取相关</li>
 *   <li>SERVICE.FILE.0022-0032：Token 访问相关</li>
 * </ul>
 *
 * @author file-service
 * @since 2026/5/12
 */
public enum FileErrorCodes implements ErrorDefinition {
  // ==================== 模板与解析相关（SERVICE.FILE.0001-0009） ====================
  CONFIG_NOT_FOUND("SERVICE.FILE.0001", "模板配置不存在"),
  CONFIG_INVALID("SERVICE.FILE.0002", "模板配置无效"),
  PARSE_FAILED("SERVICE.FILE.0003", "Excel 解析失败"),
  SUB_TASK_NOT_FOUND("SERVICE.FILE.0004", "子任务不存在"),
  SUB_TASK_EXPIRED("SERVICE.FILE.0005", "子任务已过期"),
  SUB_TASK_INVALID("SERVICE.FILE.0006", "子任务校验失败"),
  IDENTIFY_FAILED("SERVICE.FILE.0007", "无法识别源模板"),
  EXPRESSION_ERROR("SERVICE.FILE.0008", "表达式求值失败"),
  EXCEL_EXPORT_FAILED("SERVICE.FILE.0009", "Excel 模板填充失败"),

  // ==================== 文件元数据相关（SERVICE.FILE.0010-0013） ====================
  FILE_METADATA_NOT_FOUND("SERVICE.FILE.0010", "文件元数据不存在"),
  FILE_ALREADY_UPLOADED("SERVICE.FILE.0011", "文件已上传，不能重复上传"),
  FILE_STATUS_INVALID("SERVICE.FILE.0012", "文件状态不允许此操作"),
  FILE_EXPIRED("SERVICE.FILE.0013", "文件已过期"),

  // ==================== 存储后端相关（SERVICE.FILE.0014-0019） ====================
  FILE_STORAGE_FAILED("SERVICE.FILE.0014", "文件存储失败"),
  FILE_STORAGE_TARGET_NOT_FOUND("SERVICE.FILE.0015", "存储目标不存在"),
  FILE_STORAGE_TARGET_TYPE_MISMATCH("SERVICE.FILE.0016", "存储目标类型不匹配"),
  FILE_STORAGE_CONFIG_INVALID("SERVICE.FILE.0017", "存储配置无效"),
  FILE_COPY_FAILED("SERVICE.FILE.0018", "文件复制失败"),
  FILE_MD5_MISMATCH("SERVICE.FILE.0019", "文件 MD5 校验失败"),

  // ==================== 下载与读取相关（SERVICE.FILE.0020-0021） ====================
  FILE_DOWNLOAD_FAILED("SERVICE.FILE.0020", "文件下载失败"),
  FILE_STREAM_CLOSED("SERVICE.FILE.0021", "文件流已关闭"),

  // ==================== Token 访问相关（SERVICE.FILE.0022-0032） ====================
  FILE_TOKEN_INVALID("SERVICE.FILE.0022", "文件访问 token 无效或已过期"),
  FILE_TOKEN_EXPIRED("SERVICE.FILE.0023", "文件访问 token 已过期"),
  FILE_TOKEN_ALREADY_USED("SERVICE.FILE.0024", "文件访问 token 已被使用"),
  FILE_TOKEN_MISMATCH("SERVICE.FILE.0025", "文件访问 token 与当前用户不匹配"),
  FILE_CONTENT_TYPE_NOT_ALLOWED("SERVICE.FILE.0026", "文件类型不被允许"),
  FILE_SIZE_EXCEEDED("SERVICE.FILE.0027", "文件大小超出限制"),
  FILE_NOT_UPLOADABLE("SERVICE.FILE.0028", "文件当前状态不允许上传"),
  FILE_NOT_DOWNLOADABLE("SERVICE.FILE.0029", "文件当前状态不允许下载"),
  FILE_DIGEST_MISMATCH("SERVICE.FILE.0030", "文件摘要校验失败"),
  FILE_TOKEN_SECRET_NOT_CONFIGURED("SERVICE.FILE.0031", "文件 token 密钥未配置"),
  FILE_SESSION_HEADER_MISSING("SERVICE.FILE.0032", "会话 Header 缺失");

  private final String code;
  private final String message;

  FileErrorCodes(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
