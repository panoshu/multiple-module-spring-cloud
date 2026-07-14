package com.example.shared.id.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * IdErrorCode
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/2/9 22:39
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum IdErrorCode implements ErrorDefinition {

  ID_GEN_ERROR("99970", "ID生成异常: {}"),
  ID_CONFIG_ERROR("99971", "ID规则配置错误"),
  ID_STRUCTURE_ERROR("99971", "ID结构配置错误"),
  ID_FORMAT_ERROR("99971", "ID格式配置错误"),
  ID_SEGMENT_EXHAUSTED("99972", "ID号段耗尽且无法加载"),
  ID_TYPE_ERROR("99973", "ID 类型错误"),
  ID_INSTANTIATION_ERROR("99974", "ID 初始化错误"),
  ID_STRATEGY_MISSING("99975", "找不到ID策略"),

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
