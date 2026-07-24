package com.example.core.application.engine.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AllArgsConstructor;

/**
 * business-core-application 模块错误码定义。
 * <p>
 * 错误码区间 {@code 21001-21099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>5 位纯数字，首位 2 表示业务核心模块，2-3 位 10 表示 business-core-application</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 10:28
 */
@AllArgsConstructor
public enum CoreAppErrorCode implements ErrorDefinition {

  INVALID_STATUS("21001", "状态有误"),
  INVALID_DATA("21002", "数据有误"),
  INVALID_OPERATION("21003", "操作有误"),

  DATA_NOT_FOUND("21011", "数据未找到"),

  STEP_HANDLER_FAILED("21021", "步骤处理器执行失败"),

  INVALIDATE("21031", "校验失败"),

  ;

  final String code;
  final String message;

  public String code() {
    return this.code;
  }

  public String message() {
    return this.message;
  }
}
