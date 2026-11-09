package com.example.core.domain.engine.aggregate.valueobject.enums.definition;

/**
 * 步骤动作类型。外部协作类型需要声明外部配置引用。
 */
public enum ActionType {
  BUSINESS,
  FILE_PARSE,
  FILE_OUTBOUND,
  APPROVAL,
  NONE;

  public boolean requiresExternalConfig() {
    return this == FILE_PARSE || this == FILE_OUTBOUND || this == APPROVAL;
  }
}
