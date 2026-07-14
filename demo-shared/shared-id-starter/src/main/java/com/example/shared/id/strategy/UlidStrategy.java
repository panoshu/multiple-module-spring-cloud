package com.example.shared.id.strategy;

import com.example.shared.exception.SystemException;
import com.example.shared.id.algorithm.UlidAlgorithm;
import com.example.shared.id.errorcode.IdErrorCode;
import com.example.shared.primitives.identity.IdType;

public class UlidStrategy implements IdGenerationStrategy {
  @Override
  public IdType getSupportedType() {
    return IdType.ULID;
  }

  @Override
  public String nextId(IdContext context) {
    return UlidAlgorithm.generate();
  }

  @Override
  public Long nextLongId(IdContext context) {
    throw new SystemException(IdErrorCode.ID_CONFIG_ERROR)
      .withLogDetail("Long ID type is only supported for SEGMENT or SNOWFLAKE. Class: UlidStrategy");
  }

  @Override
  public boolean supportFormatting() {
    return true;
  }
}
