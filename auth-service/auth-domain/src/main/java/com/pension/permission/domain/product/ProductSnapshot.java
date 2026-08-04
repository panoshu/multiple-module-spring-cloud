package com.pension.permission.domain.product;

import com.example.shared.annuity.OperatingMode;
import com.example.shared.identifier.id.ProductNo;
import com.pension.permission.types.AccountMgrNo;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/8/3 10:12
 */
public record ProductSnapshot(
  ProductNo productNo,
  Optional<ProductNo> parentProductNo,
  String productName,
  OperatingMode operatingMode,
  AccountMgrNo accountMgrNo,
  LocalDateTime syncedAt
) {
}
