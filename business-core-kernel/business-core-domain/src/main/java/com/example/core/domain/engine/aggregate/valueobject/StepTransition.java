package com.example.core.domain.engine.aggregate.valueobject;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.exception.DomainException;

/**
 * 合法后继步骤及条件。condition 为 null 或空串表示无条件迁移；
 * 空白但非空的表达式属于错误配置，必须显式失败，不能静默当作条件不满足跳过。
 */
public record StepTransition(StepCode targetStep, String condition) implements ValueObject {

  public StepTransition {
    if (targetStep == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("后继步骤编码不能为空");
    }
    if (condition != null && !condition.isEmpty() && condition.isBlank()) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STEP_CONDITION)
        .withLogDetail("步骤条件表达式不能为空白字符");
    }
  }

  public boolean isUnconditional() {
    return condition == null || condition.isEmpty();
  }
}
