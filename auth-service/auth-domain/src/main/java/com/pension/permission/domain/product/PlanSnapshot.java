package com.pension.permission.domain.product;

import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.ProductNo;

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
