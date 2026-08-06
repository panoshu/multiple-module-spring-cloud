package com.pension.permission.domain.channel.service;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.pension.permission.domain.channel.aggregate.CustomerChannelEntitlement;
import com.pension.permission.domain.channel.errorcode.ChannelErrorCode;
import com.pension.permission.domain.channel.repository.CustomerChannelEntitlementRepository;
import com.pension.permission.domain.product.ProductGateway;

import java.util.List;

/**
 * 渠道准入策略：基于客户渠道开通记录判定登录/二次授权是否准入.
 *
 * <p>默认策略为"全关"：客户未配置 {@link CustomerChannelEntitlement} 时，
 * 所有渠道均视为未开通，拒绝登录/二次授权。</p>
 *
 * <p>本服务为无状态领域服务，依赖 {@link CustomerChannelEntitlementRepository}
 * 和 {@link ProductGateway} 两个 SPI。由应用层/基础设施层负责装配。</p>
 */
public class ChannelAccessPolicy {

  private final CustomerChannelEntitlementRepository entitlementRepository;
  private final ProductGateway productGateway;

  public ChannelAccessPolicy(
    CustomerChannelEntitlementRepository entitlementRepository,
    ProductGateway productGateway
  ) {
    this.entitlementRepository = entitlementRepository;
    this.productGateway = productGateway;
  }

  /**
   * 判断客户是否已开通指定渠道.
   *
   * <p>客户未配置开通记录时返回 false（默认全关）。</p>
   *
   * @param customerNo 客户编号
   * @param channel    登录渠道
   * @return true=已开通；false=未开通或未配置
   */
  public boolean isChannelEnabled(CustomerNo customerNo, AnnuityChannel channel) {
    return entitlementRepository.findByCustomer(customerNo)
      .map(entitlement -> entitlement.isEnabled(channel))
      .orElse(false);
  }

  /**
   * 校验计划所属客户已开通指定渠道，未开通抛 {@link DomainException}.
   *
   * <p>用于网点二次授权发起前的准入校验：通过 {@link ProductGateway} 查询计划所属客户，
   * 再校验该客户是否已开通该渠道。</p>
   *
   * @param planNo  计划编号
   * @param channel 登录渠道
   * @throws DomainException 计划不存在、客户未配置开通记录、或客户未开通该渠道时抛出
   */
  public void requireEnabledForPlan(PlanNo planNo, AnnuityChannel channel) {
    CustomerNo customerNo = productGateway.findPlan(planNo)
      .map(plan -> plan.customerNo())
      .orElseThrow(() -> new DomainException(ChannelErrorCode.CUSTOMER_CHANNEL_NOT_ENABLED)
        .withLogDetail("计划不存在，无法校验渠道开通: " + planNo)
        .withContext("planNo", planNo.value()));
    if (!isChannelEnabled(customerNo, channel)) {
      throw new DomainException(ChannelErrorCode.CUSTOMER_CHANNEL_NOT_ENABLED)
        .withLogDetail("客户未开通该登录渠道，拒绝准入")
        .withContext("customerNo", customerNo.value())
        .withContext("channel", channel.getCode());
    }
  }

  /**
   * 批量过滤计划：保留所属客户已开通指定渠道的计划.
   *
   * <p>用于网上渠道计划选择阶段：从候选计划列表中过滤掉客户未开通该渠道的计划。</p>
   *
   * @param plans   候选计划列表
   * @param channel 登录渠道
   * @return 允许办理的计划列表
   */
  public List<PlanNo> filterPlansByChannel(List<PlanNo> plans, AnnuityChannel channel) {
    if (plans.isEmpty()) {
      return List.of();
    }
    return plans.stream()
      .filter(planNo -> productGateway.findPlan(planNo)
        .map(plan -> isChannelEnabled(plan.customerNo(), channel))
        .orElse(false))
      .toList();
  }
}
