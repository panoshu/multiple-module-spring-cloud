package com.example.file.domain.model.parse.valueobject.config;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.file.domain.model.enums.RegionType;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record RegionDef(
  String name,
  RegionType type,
  String bindTo,
  RegionTrigger trigger,
  RegionStrategy strategy
) implements ValueObject {
  public RegionDef {
    if (name == null || name.isBlank()) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("RegionDef.name empty");
    }
    if (type == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("RegionDef.type null");
    }
    if (strategy == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("RegionDef.strategy null");
    }
  }
}
