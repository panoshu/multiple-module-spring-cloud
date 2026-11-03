package com.pension.permission.domain.product;

import com.example.shared.identifier.id.CustomerNo;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;

import java.time.Instant;
import java.util.Optional;

public record PlanSnapshot(
  PlanNo planNo,
  ProductNo productNo,
  CustomerNo customerNo,
  Optional<PlanNo> parentPlanNo,
  String name,
  Instant syncedAt
) {
}
