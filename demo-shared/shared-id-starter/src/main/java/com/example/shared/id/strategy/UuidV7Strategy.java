package com.example.shared.id.strategy;

import com.example.shared.exception.SystemException;
import com.example.shared.id.algorithm.UuidV7Algorithm;
import com.example.shared.id.errorcode.IdErrorCode;
import com.example.shared.primitives.identity.IdType;

public class UuidV7Strategy implements IdGenerationStrategy {
  @Override
  public IdType getSupportedType() {
    return IdType.UUID_V7;
  }

  @Override
  public String nextId(IdContext context) {
    return UuidV7Algorithm.generate().toString();
  }

  @Override
  public Long nextLongId(IdContext context) {
    throw new SystemException(IdErrorCode.ID_CONFIG_ERROR).withLogDetail("Long ID type is only supported for SEGMENT or SNOWFLAKE. Class: UuidV7Strategy");
  }

  @Override
  public boolean supportFormatting() {
    return true;
  }
}
