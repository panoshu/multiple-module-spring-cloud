package com.pension.permission.domain.channel.repository;

import com.example.shared.domain.repository.Repository;
import com.example.shared.identifier.id.CustomerNo;
import com.pension.permission.domain.channel.aggregate.CustomerChannelEntitlement;
import com.pension.permission.types.CustomerChannelEntitlementId;

import java.util.Optional;

/**
 * 客户渠道开通记录仓储接口.
 *
 * <p>本接口方法入参/出参使用领域原语和领域对象，不泄漏基础设施细节。</p>
 */
public interface CustomerChannelEntitlementRepository extends Repository<CustomerChannelEntitlement, CustomerChannelEntitlementId> {

  /**
   * 按客户编号查询渠道开通记录.
   *
   * @param customerNo 客户编号
   * @return 开通记录（若存在）；未配置时返回 {@link Optional#empty()}，
   *         由调用方（{@code ChannelAccessPolicy}）按"默认全关"策略处理
   */
  Optional<CustomerChannelEntitlement> findByCustomer(CustomerNo customerNo);
}
