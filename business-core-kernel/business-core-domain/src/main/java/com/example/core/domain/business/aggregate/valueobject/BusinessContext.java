package com.example.core.domain.business.aggregate.valueobject;

import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.shared.identifier.id.CustomerNo;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;

/**
 * BusinessContext
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 16:51
 */
public record BusinessContext(
  BusinessType businessType,
  CustomerNo customerNo,
  String customerName,
  ProductNo productNo,
  String productName,
  PlanNo planNo,
  String planName,
  OperationModel operationModel,
  AccountManager accountManager
) {
}
