package com.example.core.domain.engine.aggregate.valueobject;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.exception.DomainException;

/**
 * 流程定义版本。流程实例启动时固定该版本，定义升级不影响在途实例。
 */
public record DefinitionVersion(String value) implements ValueObject {

  private static final String INITIAL_VERSION = "v1";

  public DefinitionVersion {
    if (value == null || value.isBlank()) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("流程定义版本不能为空");
    }
  }

  public static DefinitionVersion of(String value) {
    return new DefinitionVersion(value);
  }

  /** 存量数据未记录版本时使用的初始版本，与默认流程定义版本一致。 */
  public static DefinitionVersion initial() {
    return new DefinitionVersion(INITIAL_VERSION);
  }
}
