package com.example.shared.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基础异常类（抽象）。
 * <p>
 * 设计要点：
 * <ol>
 *   <li>身份与动态分离：ErrorDefinition 只提供静态身份和标准话术，动态信息通过 withXxx 传入。</li>
 *   <li>面向用户与面向运维分离：userDetail 仅供前端展示补充说明，logContext 专供日志/ELK 做结构化检索。</li>
 *   <li>纯粹无依赖：不包含任何日志框架依赖和字符串格式化逻辑，只做纯粹的载体。</li>
 *   <li>线程安全防御：对外暴露的上下文为只读视图，防止在全局异常处理器等外部环境中被意外修改。</li>
 *   <li>抽象强制：本类为抽象类，业务方只能实例化其子类（{@link DomainException}、
 *       {@link BusinessException}、{@link SystemException}），避免抛出语义不明的"裸"基础异常。</li>
 * </ol>
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/19 14:52
 */
public abstract class BaseException extends RuntimeException {

  /**
   * 错误码枚举，定义异常的静态身份和标准用户提示
   */
  private final ErrorDefinition errorDefinition;
  /**
   * 面向运维的结构化上下文
   * 使用 LinkedHashMap 保证插入顺序，方便日志阅读和 ELK 索引
   */
  private final Map<String, Object> logContext = new LinkedHashMap<>();
  /**
   * 面向用户的动态补充说明
   * 例如：ErrorCode定义为"库存不足"，detail可补充为"当前库存为5"
   */
  private String userDetail;
  /**
   * 面向日志的动态补充说明
   * 例如：ErrorCode定义为"缓存异常"，detail可补充为"key失效"
   */
  private String logDetail;

  /**
   * 受保护构造函数，仅供子类调用。
   *
   * @param errorDefinition 错误码定义，不能为 null
   */
  protected BaseException(ErrorDefinition errorDefinition) {
    super(errorDefinition.errorInfo());
    this.errorDefinition = errorDefinition;
  }

  /**
   * 受保护构造函数，仅供子类调用。
   *
   * @param errorDefinition 错误码定义，不能为 null
   * @param cause           原始异常
   */
  protected BaseException(ErrorDefinition errorDefinition, Throwable cause) {
    super(errorDefinition.errorInfo(), cause);
    this.errorDefinition = errorDefinition;
  }

  /**
   * 追加面向用户的动态补充说明（支持链式调用）
   *
   * @param detail 完整的补充说明文案
   */
  public BaseException withUserDetail(String detail) {
    this.userDetail = detail;
    return this;
  }

  /**
   * 追加面向日志的动态补充说明（支持链式调用）
   *
   * @param detail 完整的补充说明文案
   */
  public BaseException withLogDetail(String detail) {
    this.logDetail = detail;
    return this;
  }

  /**
   * 追加面向运维的结构化上下文（支持链式调用）
   *
   * @param key   上下文键，如 "userId", "orderId"
   * @param value 上下文值
   */
  public BaseException withContext(String key, Object value) {
    this.logContext.put(key, value);
    return this;
  }

  /**
   * 获取最终展示给前端的完整信息
   * 自动拼接标准话术和补充说明（如果有的话，用逗号隔开）
   *
   * @return 完整的用户提示信息
   */
  public String displayMessage() {
    if (userDetail == null || userDetail.isBlank()) {
      return errorDefinition.getMessage();
    }

    return errorDefinition.getMessage() + "，" + userDetail;
  }

  /**
   * 获取面向运维和日志的完整上下文信息
   * 自动拼接 ErrorInfo、LogDetail 和 LogContext
   *
   * @return 完整的日志排查信息
   */
  public String logMessage() {
    var errorInfo = errorDefinition != null ? errorDefinition.errorInfo() : "UNKNOWN_ERROR";
    StringBuilder builder = new StringBuilder(errorInfo);

    if (logDetail != null && !logDetail.isBlank()) {
      builder.append(" | LogDetail: [").append(logDetail).append("]");
    }

    if (!logContext.isEmpty()) {
      builder.append(" | Context: ").append(logContext);
    }

    return builder.toString();
  }

  /**
   * 获取面向运维的结构化上下文
   * 返回只读视图，杜绝在全局异常处理器等外部环境中意外修改，保障线程安全
   *
   * @return 不可修改的 Map 视图
   */
  public Map<String, Object> getLogContext() {
    return Collections.unmodifiableMap(logContext);
  }

  public String code() {
    return errorDefinition.getCode();
  }

}
