package com.pension.permission.domain.product;

import com.example.shared.identifier.id.CustomerNo;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 客户信息的只读投影。数据主权在外部系统，本系统通过防腐层(OrgDirectory)
 * 同步过来使用，不承担维护客户主数据的职责。
 */
public record CustomerSnapshot(
  CustomerNo customerNo,
  Optional<CustomerNo> parentCustomerNo,
  String name,
  LocalDateTime syncedAt
) {
}
