package com.example.core.application.engine.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AllArgsConstructor;

/**
 * business-core-application 模块错误码定义。
 * <p>
 * 错误码区间 {@code CORE.APP.0001-CORE.APP.0099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式：CORE.APP.XXXX（业务核心模块 - business-core-application）</li>
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

  INVALID_STATUS("CORE.APP.0001", "状态有误"),
  INVALID_DATA("CORE.APP.0002", "数据有误"),
  INVALID_OPERATION("CORE.APP.0003", "操作有误"),

  DATA_NOT_FOUND("CORE.APP.0004", "数据未找到"),

  STEP_HANDLER_FAILED("CORE.APP.0005", "步骤处理器执行失败"),

  INVALIDATE("CORE.APP.0006", "校验失败"),

  ;

  final String code;
  final String message;

  public String getCode() {
    return this.code;
  }

  public String getMessage() {
    return this.message;
  }
}
