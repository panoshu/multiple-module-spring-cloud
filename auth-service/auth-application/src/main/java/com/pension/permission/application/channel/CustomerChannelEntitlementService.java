package com.pension.permission.application.channel;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.contract.IdService;
import com.pension.permission.api.command.DisableChannelCommand;
import com.pension.permission.api.command.EnableChannelCommand;
import com.pension.permission.api.command.ReplaceChannelsCommand;
import com.pension.permission.api.dto.CustomerChannelEntitlementResponse;
import com.pension.permission.api.query.GetEntitlementQuery;
import com.pension.permission.domain.channel.aggregate.CustomerChannelEntitlement;
import com.pension.permission.domain.channel.errorcode.ChannelErrorCode;
import com.pension.permission.domain.channel.repository.CustomerChannelEntitlementRepository;
import com.pension.permission.types.CustomerChannelEntitlementId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户渠道开通记录应用服务.
 *
 * <p>编排客户渠道开通的用例：查询、开通、关闭、批量替换。
 * 业务规则由 {@link CustomerChannelEntitlement} 聚合根负责，本类仅做流程编排与事务管理。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerChannelEntitlementService {

  private final CustomerChannelEntitlementRepository repository;
  private final IdService idService;

  /**
   * 查询客户的渠道开通记录.
   *
   * <p>客户未配置任何渠道时返回 {@code null}，表示"默认全关"的合法业务状态。</p>
   *
   * @param query 查询对象
   * @return 开通记录响应；不存在时返回 {@code null}
   */
  @Transactional(readOnly = true)
  public CustomerChannelEntitlementResponse getEntitlement(GetEntitlementQuery query) {
    return repository.findByCustomer(query.customerNo())
      .map(this::toResponse)
      .orElse(null);
  }

  /**
   * 开通渠道.
   *
   * <p>若客户尚无开通记录，则创建一条初始包含该渠道的记录；
   * 若已有记录，则调用聚合根的 {@code enable} 方法追加渠道。</p>
   *
   * @param command 开通命令
   * @return 开通后的记录响应
   */
  @Transactional
  public CustomerChannelEntitlementResponse enable(EnableChannelCommand command) {
    Optional<CustomerChannelEntitlement> existing = repository.findByCustomer(command.customerNo());
    CustomerChannelEntitlement entitlement;
    if (existing.isPresent()) {
      entitlement = existing.get();
      entitlement.enable(command.channel(), command.operator());
    } else {
      entitlement = createEntitlement(command.customerNo(), Set.of(command.channel()), command.operator());
    }
    repository.save(entitlement);
    return toResponse(entitlement);
  }

  /**
   * 关闭渠道.
   *
   * <p>客户无开通记录时抛 {@link BusinessException}。</p>
   *
   * @param command 关闭命令
   * @return 关闭后的记录响应
   */
  @Transactional
  public CustomerChannelEntitlementResponse disable(DisableChannelCommand command) {
    CustomerChannelEntitlement entitlement = repository.findByCustomer(command.customerNo())
      .orElseThrow(() -> new BusinessException(ChannelErrorCode.CUSTOMER_CHANNEL_NOT_ENABLED)
        .withUserDetail("客户未配置任何渠道开通记录"));
    entitlement.disable(command.channel(), command.operator());
    repository.save(entitlement);
    return toResponse(entitlement);
  }

  /**
   * 批量替换渠道集合.
   *
   * <p>客户无开通记录时抛 {@link BusinessException}。</p>
   *
   * @param command 替换命令
   * @return 替换后的记录响应
   */
  @Transactional
  public CustomerChannelEntitlementResponse replace(ReplaceChannelsCommand command) {
    CustomerChannelEntitlement entitlement = repository.findByCustomer(command.customerNo())
      .orElseThrow(() -> new BusinessException(ChannelErrorCode.CUSTOMER_CHANNEL_NOT_ENABLED)
        .withUserDetail("客户未配置任何渠道开通记录"));
    entitlement.replaceChannels(command.channels(), command.operator());
    repository.save(entitlement);
    return toResponse(entitlement);
  }

  /**
   * 创建新的客户渠道开通记录.
   */
  private CustomerChannelEntitlement createEntitlement(
    com.example.shared.identifier.id.CustomerNo customerNo,
    Set<AnnuityChannel> channels,
    com.example.shared.identifier.id.UserNo operator
  ) {
    CustomerChannelEntitlementId id = idService.nextId(CustomerChannelEntitlementId.class);
    return CustomerChannelEntitlement.create(
      new CustomerChannelEntitlement.CreateContext(id, customerNo, channels, operator));
  }

  /**
   * 将聚合根转换为响应 DTO.
   */
  private CustomerChannelEntitlementResponse toResponse(CustomerChannelEntitlement entitlement) {
    Set<String> channelNames = entitlement.enabledChannels().stream()
      .map(AnnuityChannel::name)
      .collect(Collectors.toSet());
    return new CustomerChannelEntitlementResponse(
      entitlement.id().value(),
      entitlement.customerNo().value(),
      channelNames
    );
  }
}
