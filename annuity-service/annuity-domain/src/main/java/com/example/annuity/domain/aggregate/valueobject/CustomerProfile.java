package com.example.annuity.domain.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.identifier.id.CustomerNo;

import java.util.List;

/**
 * 客户画像值对象
 *
 * @param customerNo       客户编号
 * @param riskLevel        风险等级(LOW/MEDIUM/HIGH)
 * @param relatedCompanies 关联企业列表
 * @author annuity-service
 * @since 2026/7/22
 */
public record CustomerProfile(
  CustomerNo customerNo,
  String riskLevel,
  List<String> relatedCompanies
) implements ValueObject {

  public CustomerProfile {
    if (customerNo == null) {
      throw new IllegalArgumentException("customerNo cannot be null");
    }
    relatedCompanies = relatedCompanies == null ? List.of() : List.copyOf(relatedCompanies);
  }

  /**
   * 判断客户关联企业是否包含外资标识
   */
  public boolean hasForeignCompany() {
    return relatedCompanies.stream().anyMatch(c -> c.contains("FOREIGN"));
  }
}
