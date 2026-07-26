package com.example.iam.domain.authorization.gateway;

import com.example.iam.domain.authorization.aggregate.valueobject.CustomerInfo;
import java.util.Optional;

/**
 * 客户信息查询网关 - 防腐层接口,从外部业务系统加载客户基础信息。
 *
 * <p>用于客户级权限规则匹配与客户类型校验。
 *
 * <p>实现位于 infrastructure 层,将外部 DTO 转换为领域值对象 {@link CustomerInfo}。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface CustomerGateway {

  /**
   * 按客户编号查询客户信息。
   *
   * @param customerNo 客户编号(外部系统标识)
   * @return 客户信息;不存在时返回 {@link Optional#empty()}
   */
  Optional<CustomerInfo> findByCustomerNo(String customerNo);
}
