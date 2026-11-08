package com.example.auth.adapter.converter;

import com.example.auth.api.command.*;
import com.example.auth.api.query.GetEntitlementQuery;
import com.example.auth.api.query.GetEntitlementRequest;
import com.example.core.types.Channel;
import com.example.shared.enumeration.EnumCodeCache;
import com.example.core.types.CustomerNo;
import com.example.core.types.UserNo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户渠道开通记录 Adapter 层转换器.
 *
 * <p>负责请求体 DTO 与领域 Command/Query 之间的转换。
 * String→CustomerNo、String→Channel 通过 {@link Named} 标注的 default 方法完成。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class CustomerChannelEntitlementConverter {

  @Mapping(source = "request.customerNo", target = "customerNo", qualifiedByName = "toCustomerNo")
  @Mapping(source = "request.channelType", target = "channel", qualifiedByName = "toAnnuityChannel")
  @Mapping(source = "operator", target = "operator")
  public abstract EnableChannelCommand toCommand(EnableChannelRequest request, UserNo operator);

  @Mapping(source = "request.customerNo", target = "customerNo", qualifiedByName = "toCustomerNo")
  @Mapping(source = "request.channelType", target = "channel", qualifiedByName = "toAnnuityChannel")
  @Mapping(source = "operator", target = "operator")
  public abstract DisableChannelCommand toCommand(DisableChannelRequest request, UserNo operator);

  @Mapping(source = "request.customerNo", target = "customerNo", qualifiedByName = "toCustomerNo")
  @Mapping(source = "request.channelTypes", target = "channels", qualifiedByName = "toAnnuityChannelSet")
  @Mapping(source = "operator", target = "operator")
  public abstract ReplaceChannelsCommand toCommand(ReplaceChannelsRequest request, UserNo operator);

  @Mapping(source = "customerNo", target = "customerNo", qualifiedByName = "toCustomerNo")
  public abstract GetEntitlementQuery toQuery(GetEntitlementRequest request);

  @Named("toCustomerNo")
  protected CustomerNo toCustomerNo(String value) {
    return CustomerNo.of(value);
  }

  @Named("toAnnuityChannel")
  protected Channel toAnnuityChannel(String channel) {
    return EnumCodeCache.get(Channel.class, channel);
  }

  @Named("toAnnuityChannelSet")
  protected Set<Channel> toAnnuityChannelSet(List<String> channels) {
    if (channels == null) {
      return Set.of();
    }
    return channels.stream()
      .map(Channel::valueOf)
      .collect(Collectors.toSet());
  }
}
