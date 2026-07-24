package com.example.shared.id.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * shared-id-starter 模块错误码定义。
 * <p>
 * 错误码区间 {@code SHARED.ID.0001-SHARED.ID.0099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式：SHARED.ID.XXXX（公共基础模块 - shared-id-starter）</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/2/9 22:39
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum IdErrorCode implements ErrorDefinition {

  ID_GEN_ERROR("SHARED.ID.0001", "ID生成异常"),
  ID_CONFIG_ERROR("SHARED.ID.0002", "ID规则配置错误"),
  ID_STRUCTURE_ERROR("SHARED.ID.0003", "ID结构配置错误"),
  ID_FORMAT_ERROR("SHARED.ID.0004", "ID格式配置错误"),
  ID_SEGMENT_EXHAUSTED("SHARED.ID.0005", "ID号段耗尽且无法加载"),
  ID_TYPE_ERROR("SHARED.ID.0006", "ID类型错误"),
  ID_INSTANTIATION_ERROR("SHARED.ID.0007", "ID初始化错误"),
  ID_STRATEGY_MISSING("SHARED.ID.0008", "找不到ID策略"),

  ;

  private final String code;
  private final String message;

  public String code() {
    return this.code;
  }

  public String message() {
    return this.message;
  }
}
