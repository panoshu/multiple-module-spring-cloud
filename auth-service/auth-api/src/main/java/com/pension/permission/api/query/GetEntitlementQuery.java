package com.pension.permission.api.query;

import com.example.shared.identifier.id.CustomerNo;

/**
 * 查询客户渠道开通记录查询对象.
 *
 * @param customerNo 客户编号
 */
public record GetEntitlementQuery(
  CustomerNo customerNo
) {}
