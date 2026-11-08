package com.example.shared.exception;

/**
 * 通用错误码定义。
 * <p>
 * 码段规则（参见 {@code 08-错误码规范.md}）：
 * <ul>
 *   <li>通用码段：COMMON.XXXX</li>
 *   <li>COMMON.0000：操作成功</li>
 *   <li>COMMON.0001-COMMON.0049：4xx 类客户端错误（{@link BusinessException}）</li>
 *   <li>COMMON.0050-COMMON.0099：5xx 类服务端错误（{@link SystemException}）</li>
 * </ul>
 * 消息内容使用纯文本，禁止使用 {@code {}} 占位符；动态上下文通过
 * {@link BaseException#withUserDetail(String)} / {@link BaseException#withLogDetail(String)}
 * / {@link BaseException#withContext(String, Object)} 附加。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/19 14:53
 */
public enum CommonError implements ErrorDefinition {

  // ==================== 通用成功（通常用于统一响应封装） ====================
  SUCCESS("COMMON.0000", "操作成功"),

  // ==================== 客户端请求错误 4xx 系列（COMMON.0001-COMMON.0049） ====================
  BAD_REQUEST("COMMON.0001", "请求参数错误"),
  UNAUTHORIZED("COMMON.0002", "未登录或登录已过期"),
  FORBIDDEN("COMMON.0003", "无权限访问"),
  NOT_FOUND("COMMON.0004", "请求资源不存在"),
  METHOD_NOT_ALLOWED("COMMON.0005", "请求方法不允许"),
  TOO_MANY_REQUESTS("COMMON.0006", "请求过于频繁，请稍后再试"),

  // ==================== 系统执行错误 5xx 系列（COMMON.0050-COMMON.0099） ====================
  INTERNAL_SERVER_ERROR("COMMON.0050", "系统开小差了，请稍后再试"),
  SERVICE_DEGRADATION("COMMON.0051", "系统负载过高，服务降级"),
  REMOTE_SERVICE_ERROR("COMMON.0052", "第三方服务调用失败"),
  NETWORK_ERROR("COMMON.0053", "网络异常，请稍后再试"),
  CONCURRENCY_ERROR("COMMON.0054", "并发错误"),
  TIMEOUT_ERROR("COMMON.0055", "请求超时"),
  UNKNOWN_ERROR("COMMON.0099", "系统开小差了，请联系管理员");

  private final String code;
  private final String message;

  CommonError(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String getCode() {
    return this.code;
  }

  @Override
  public String getMessage() {
    return this.message;
  }
}
