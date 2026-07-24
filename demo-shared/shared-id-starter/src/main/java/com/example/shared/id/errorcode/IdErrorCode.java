package com.example.shared.id.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * shared-id-starter 模块错误码定义。
 * <p>
 * 错误码区间 {@code 14001-14099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>5 位纯数字，首位 1 表示公共基础模块，2-3 位 40 表示 shared-id-starter</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/2/9 22:39
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum IdErrorCode implements ErrorDefinition {

  ID_GEN_ERROR("14001", "ID生成异常"),
  ID_CONFIG_ERROR("14002", "ID规则配置错误"),
  ID_STRUCTURE_ERROR("14003", "ID结构配置错误"),
  ID_FORMAT_ERROR("14004", "ID格式配置错误"),
  ID_SEGMENT_EXHAUSTED("14005", "ID号段耗尽且无法加载"),
  ID_TYPE_ERROR("14006", "ID类型错误"),
  ID_INSTANTIATION_ERROR("14007", "ID初始化错误"),
  ID_STRATEGY_MISSING("14008", "找不到ID策略"),

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
