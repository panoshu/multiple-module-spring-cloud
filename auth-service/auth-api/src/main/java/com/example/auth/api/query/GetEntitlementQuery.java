package com.example.auth.api.query;

/**
 * 查询客户渠道开通记录查询对象.
 *
 * @param customerNo 客户编号
 */
public record GetEntitlementQuery(
  String customerNo
) {
}
