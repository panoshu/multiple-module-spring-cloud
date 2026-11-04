package com.example.auth.domain.channel.aggregate;

import com.example.core.types.Channel;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.core.types.CustomerNo;
import com.example.core.types.UserNo;
import com.example.auth.domain.channel.errorcode.ChannelErrorCode;
import com.example.auth.domain.channel.event.ChannelDisabled;
import com.example.auth.domain.channel.event.ChannelEnabled;
import com.example.auth.domain.channel.event.CustomerChannelEntitlementCreated;
import com.example.auth.domain.channel.event.CustomerChannelEntitlementReplaced;
import com.example.auth.types.CustomerChannelEntitlementId;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 客户渠道开通聚合根.
 *
 * <p>记录某客户开通了哪些登录渠道（网上/网点等）。只有客户开通的渠道，
 * 该客户名下计划上的经办人才能使用这些方式登录或进行二次授权。</p>
 *
 * <p>语义规则：
 * <ul>
 *   <li>开通的渠道集合非空（至少开通一个渠道）</li>
 *   <li>重复开通已开通的渠道抛 {@link DomainException}</li>
 *   <li>关闭未开通的渠道抛 {@link DomainException}</li>
 *   <li>关闭最后一个渠道抛 {@link DomainException}（避免客户完全没有可用渠道）</li>
 * </ul>
 * </p>
 */
public class CustomerChannelEntitlement extends AggregateRoot<CustomerChannelEntitlementId> {

  private final CustomerNo customerNo;
  private Set<Channel> enabledChannels;

  private CustomerChannelEntitlement(
    CustomerChannelEntitlementId id, UserNo creator,
    CustomerNo customerNo,
    Set<Channel> enabledChannels
  ) {
    super(id, creator.value());
    this.customerNo = customerNo;
    this.enabledChannels = new HashSet<>(enabledChannels);
    validateInvariants();
    registerDomainEvent(CustomerChannelEntitlementCreated.of(
      id, customerNo, Set.copyOf(this.enabledChannels), creator));
  }

  private CustomerChannelEntitlement(
    CustomerChannelEntitlementId id,
    String createdBy, String updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt,
    Version version,
    CustomerNo customerNo,
    Set<Channel> enabledChannels
  ) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.customerNo = customerNo;
    this.enabledChannels = new HashSet<>(enabledChannels);
    validateInvariants();
  }

  /**
   * 业务创建客户渠道开通记录.
   */
  public static CustomerChannelEntitlement create(CreateContext ctx) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(ctx.id(), "id");
    Objects.requireNonNull(ctx.customerNo(), "customerNo");
    Objects.requireNonNull(ctx.enabledChannels(), "enabledChannels");
    Objects.requireNonNull(ctx.operator(), "operator");
    return new CustomerChannelEntitlement(
      ctx.id(), ctx.operator(),
      ctx.customerNo(), ctx.enabledChannels());
  }

  /**
   * 从持久化数据重建聚合根（不注册领域事件）.
   */
  public static CustomerChannelEntitlement reconstitute(ReconstituteSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    return new CustomerChannelEntitlement(
      snapshot.id(),
      snapshot.createdBy(), snapshot.updatedBy(),
      snapshot.createdAt(), snapshot.updatedAt(),
      snapshot.version(),
      snapshot.customerNo(),
      snapshot.enabledChannels());
  }

  public CustomerNo customerNo() {
    return customerNo;
  }

  public Set<Channel> enabledChannels() {
    return Set.copyOf(enabledChannels);
  }

  public boolean isEnabled(Channel channel) {
    return enabledChannels.contains(channel);
  }

  /**
   * 开通某渠道.
   *
   * @throws DomainException 渠道已开通时抛出
   */
  public void enable(Channel channel, UserNo operator) {
    Objects.requireNonNull(channel, "channel");
    Objects.requireNonNull(operator, "operator");
    if (enabledChannels.contains(channel)) {
      throw new DomainException(ChannelErrorCode.CUSTOMER_CHANNEL_NOT_ENABLED)
        .withLogDetail("渠道已开通，不能重复开通: " + channel);
    }
    enabledChannels.add(channel);
    registerDomainEvent(ChannelEnabled.of(id(), customerNo, channel, operator));
    markUpdated(operator.value());
  }

  /**
   * 关闭某渠道.
   *
   * @throws DomainException 渠道未开通，或关闭后无可用渠道时抛出
   */
  public void disable(Channel channel, UserNo operator) {
    Objects.requireNonNull(channel, "channel");
    Objects.requireNonNull(operator, "operator");
    if (!enabledChannels.contains(channel)) {
      throw new DomainException(ChannelErrorCode.CUSTOMER_CHANNEL_NOT_ENABLED)
        .withLogDetail("渠道未开通，不能关闭: " + channel);
    }
    if (enabledChannels.size() <= 1) {
      throw new DomainException(ChannelErrorCode.CUSTOMER_CHANNEL_NOT_ENABLED)
        .withLogDetail("不能关闭最后一个可用渠道: " + channel);
    }
    enabledChannels.remove(channel);
    registerDomainEvent(ChannelDisabled.of(id(), customerNo, channel, operator));
    markUpdated(operator.value());
  }

  /**
   * 批量替换开通的渠道集合.
   *
   * @throws DomainException 新渠道集合为空时抛出
   */
  public void replaceChannels(Set<Channel> channels, UserNo operator) {
    Objects.requireNonNull(channels, "channels");
    Objects.requireNonNull(operator, "operator");
    if (channels.isEmpty()) {
      throw new DomainException(ChannelErrorCode.CUSTOMER_CHANNEL_NOT_ENABLED)
        .withLogDetail("渠道集合不能为空");
    }
    Set<Channel> oldChannels = Set.copyOf(this.enabledChannels);
    this.enabledChannels = new HashSet<>(channels);
    registerDomainEvent(CustomerChannelEntitlementReplaced.of(
      id(), customerNo, oldChannels, Set.copyOf(channels), operator));
    markUpdated(operator.value());
  }

  @Override
  protected void validateInvariants() {
    if (customerNo == null) {
      throw new DomainException(ChannelErrorCode.CUSTOMER_CHANNEL_NOT_ENABLED)
        .withLogDetail("customerNo cannot be null");
    }
    if (enabledChannels == null || enabledChannels.isEmpty()) {
      throw new DomainException(ChannelErrorCode.CUSTOMER_CHANNEL_NOT_ENABLED)
        .withLogDetail("enabledChannels cannot be null or empty");
    }
  }

  /**
   * 创建客户渠道开通记录的参数对象.
   *
   * <p>封装 {@link #create(CreateContext)} 所需的全部入参，避免方法参数超标
   * （规则 04 §10.1：单个方法参数不超过 5 个）。</p>
   */
  public record CreateContext(
    CustomerChannelEntitlementId id,
    CustomerNo customerNo,
    Set<Channel> enabledChannels,
    UserNo operator
  ) {
  }

  /**
   * 从持久化数据重建聚合根的快照参数对象.
   */
  public record ReconstituteSnapshot(
    CustomerChannelEntitlementId id,
    String createdBy, String updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt,
    Version version,
    CustomerNo customerNo,
    Set<Channel> enabledChannels
  ) {
  }
}
