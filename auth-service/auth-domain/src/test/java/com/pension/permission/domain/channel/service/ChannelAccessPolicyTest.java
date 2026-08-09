package com.pension.permission.domain.channel.service;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.ProductNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.CustomerChannelEntitlement;
import com.pension.permission.domain.channel.repository.CustomerChannelEntitlementRepository;
import com.pension.permission.domain.product.PlanSnapshot;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.types.CustomerChannelEntitlementId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ChannelAccessPolicy 领域服务测试")
class ChannelAccessPolicyTest {

  private static final CustomerNo CUSTOMER_NO = CustomerNo.of("C-001");
  private static final PlanNo PLAN_1 = PlanNo.of("P-1");
  private static final PlanNo PLAN_2 = PlanNo.of("P-2");
  private static final PlanNo PLAN_3 = PlanNo.of("P-3");

  private CustomerChannelEntitlementRepository entitlementRepository;
  private ProductGateway productGateway;
  private ChannelAccessPolicy policy;

  @BeforeEach
  void setUp() {
    entitlementRepository = mock(CustomerChannelEntitlementRepository.class);
    productGateway = mock(ProductGateway.class);
    policy = new ChannelAccessPolicy(entitlementRepository, productGateway);

    // 默认：三个计划都归属同一个客户
    lenient().when(productGateway.findPlan(PLAN_1))
      .thenReturn(Optional.of(planSnapshot(PLAN_1, CUSTOMER_NO)));
    lenient().when(productGateway.findPlan(PLAN_2))
      .thenReturn(Optional.of(planSnapshot(PLAN_2, CUSTOMER_NO)));
    lenient().when(productGateway.findPlan(PLAN_3))
      .thenReturn(Optional.of(planSnapshot(PLAN_3, CUSTOMER_NO)));
  }

  private PlanSnapshot planSnapshot(PlanNo planNo, CustomerNo customerNo) {
    return new PlanSnapshot(planNo, ProductNo.of("PRD-1"), customerNo, Optional.empty(), "plan", null);
  }

  private CustomerChannelEntitlement entitlementWithChannels(Set<AnnuityChannel> channels) {
    return CustomerChannelEntitlement.create(
      new CustomerChannelEntitlement.CreateContext(
        new CustomerChannelEntitlementId("e-1"),
        CUSTOMER_NO,
        channels,
        UserNo.of("admin")));
  }

  @Nested
  @DisplayName("isChannelEnabled: 客户渠道开通状态查询")
  class IsChannelEnabledTest {

    @Test
    @DisplayName("客户已开通该渠道应返回 true")
    void shouldReturnTrueWhenChannelEnabled() {
      when(entitlementRepository.findByCustomer(CUSTOMER_NO))
        .thenReturn(Optional.of(entitlementWithChannels(Set.of(AnnuityChannel.NETAPP))));

      boolean result = policy.isChannelEnabled(CUSTOMER_NO, AnnuityChannel.NETAPP);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("客户未开通该渠道应返回 false")
    void shouldReturnFalseWhenChannelNotEnabled() {
      when(entitlementRepository.findByCustomer(CUSTOMER_NO))
        .thenReturn(Optional.of(entitlementWithChannels(Set.of(AnnuityChannel.TELLER))));

      boolean result = policy.isChannelEnabled(CUSTOMER_NO, AnnuityChannel.NETAPP);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("客户未配置开通记录（默认全关）应返回 false")
    void shouldReturnFalseWhenEntitlementNotConfigured() {
      when(entitlementRepository.findByCustomer(CUSTOMER_NO))
        .thenReturn(Optional.empty());

      boolean result = policy.isChannelEnabled(CUSTOMER_NO, AnnuityChannel.NETAPP);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("requireEnabledForPlan: 按计划校验渠道开通")
  class RequireEnabledForPlanTest {

    @Test
    @DisplayName("计划所属客户已开通该渠道应通过校验")
    void shouldPassWhenChannelEnabled() {
      when(entitlementRepository.findByCustomer(CUSTOMER_NO))
        .thenReturn(Optional.of(entitlementWithChannels(Set.of(AnnuityChannel.BANK_BRANCH))));

      policy.requireEnabledForPlan(PLAN_1, AnnuityChannel.BANK_BRANCH);

      // 无异常即通过
    }

    @Test
    @DisplayName("计划所属客户未开通该渠道应抛 DomainException")
    void shouldThrowWhenChannelNotEnabled() {
      when(entitlementRepository.findByCustomer(CUSTOMER_NO))
        .thenReturn(Optional.of(entitlementWithChannels(Set.of(AnnuityChannel.NETAPP))));

      assertThatThrownBy(() -> policy.requireEnabledForPlan(PLAN_1, AnnuityChannel.BANK_BRANCH))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("计划所属客户未配置开通记录（默认全关）应抛 DomainException")
    void shouldThrowWhenEntitlementNotConfigured() {
      when(entitlementRepository.findByCustomer(CUSTOMER_NO))
        .thenReturn(Optional.empty());

      assertThatThrownBy(() -> policy.requireEnabledForPlan(PLAN_1, AnnuityChannel.BANK_BRANCH))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("计划不存在应抛 DomainException")
    void shouldThrowWhenPlanNotFound() {
      when(productGateway.findPlan(PLAN_1)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> policy.requireEnabledForPlan(PLAN_1, AnnuityChannel.BANK_BRANCH))
        .isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("filterPlansByChannel: 批量过滤计划")
  class FilterPlansByChannelTest {

    @Test
    @DisplayName("应过滤掉客户未开通该渠道的计划")
    void shouldFilterOutPlansWhereChannelNotEnabled() {
      when(entitlementRepository.findByCustomer(CUSTOMER_NO))
        .thenReturn(Optional.of(entitlementWithChannels(Set.of(AnnuityChannel.NETAPP))));

      List<PlanNo> result = policy.filterPlansByChannel(
        List.of(PLAN_1, PLAN_2, PLAN_3), AnnuityChannel.NETAPP);

      assertThat(result).containsExactlyInAnyOrder(PLAN_1, PLAN_2, PLAN_3);
    }

    @Test
    @DisplayName("客户未配置开通记录应过滤掉全部计划")
    void shouldFilterOutAllWhenEntitlementNotConfigured() {
      when(entitlementRepository.findByCustomer(CUSTOMER_NO))
        .thenReturn(Optional.empty());

      List<PlanNo> result = policy.filterPlansByChannel(
        List.of(PLAN_1, PLAN_2, PLAN_3), AnnuityChannel.NETAPP);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应只保留客户已开通渠道的计划")
    void shouldKeepOnlyPlansForEnabledChannel() {
      when(entitlementRepository.findByCustomer(CUSTOMER_NO))
        .thenReturn(Optional.of(entitlementWithChannels(Set.of(AnnuityChannel.NETAPP))));

      List<PlanNo> result = policy.filterPlansByChannel(
        List.of(PLAN_1, PLAN_2, PLAN_3), AnnuityChannel.BANK_BRANCH);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空计划列表应返回空列表，不调用仓储")
    void shouldReturnEmptyForEmptyInput() {
      List<PlanNo> result = policy.filterPlansByChannel(List.of(), AnnuityChannel.NETAPP);

      assertThat(result).isEmpty();
      verify(entitlementRepository, never()).findByCustomer(any());
    }
  }
}
