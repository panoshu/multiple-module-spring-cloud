package com.example.annuity.domain.gateway;

import com.example.annuity.domain.aggregate.valueobject.CustomerProfile;
import com.example.shared.identifier.id.CustomerNo;

/**
 * 年金客户网关接口
 * <p>
 * 防腐层接口,供 application 层的扩展动作查询客户画像。
 * 由 infrastructure 层提供 Mock 或真实实现。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public interface AnnuityCustomerGateway {

  /**
   * 查询客户画像
   *
   * @param customerNo 客户编号
   * @return 客户画像
   */
  CustomerProfile queryCustomer(CustomerNo customerNo);
}
