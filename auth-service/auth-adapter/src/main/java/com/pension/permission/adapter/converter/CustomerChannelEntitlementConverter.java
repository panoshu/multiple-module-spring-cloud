package com.pension.permission.adapter.converter;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.api.command.DisableChannelCommand;
import com.pension.permission.api.command.EnableChannelCommand;
import com.pension.permission.api.command.ReplaceChannelsCommand;
import com.pension.permission.api.dto.DisableChannelRequest;
import com.pension.permission.api.dto.EnableChannelRequest;
import com.pension.permission.api.dto.GetEntitlementRequest;
import com.pension.permission.api.dto.ReplaceChannelsRequest;
import com.pension.permission.api.query.GetEntitlementQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

/**
 * 客户渠道开通记录 Adapter 层转换器.
 *
 * <p>负责请求体 DTO 与领域 Command/Query 之间的转换。
 * String→CustomerNo、String→AnnuityChannel 通过 {@link Named} 标注的 default 方法完成。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class CustomerChannelEntitlementConverter {

  @Mapping(source = "request.customerNo", target = "customerNo", qualifiedByName = "toCustomerNo")
  @Mapping(source = "request.channel", target = "channel", qualifiedByName = "toAnnuityChannel")
  @Mapping(source = "operator", target = "operator")
  public abstract EnableChannelCommand toCommand(EnableChannelRequest request, UserNo operator);

  @Mapping(source = "request.customerNo", target = "customerNo", qualifiedByName = "toCustomerNo")
  @Mapping(source = "request.channel", target = "channel", qualifiedByName = "toAnnuityChannel")
  @Mapping(source = "operator", target = "operator")
  public abstract DisableChannelCommand toCommand(DisableChannelRequest request, UserNo operator);

  @Mapping(source = "request.customerNo", target = "customerNo", qualifiedByName = "toCustomerNo")
  @Mapping(source = "request.channels", target = "channels", qualifiedByName = "toAnnuityChannel")
  @Mapping(source = "operator", target = "operator")
  public abstract ReplaceChannelsCommand toCommand(ReplaceChannelsRequest request, UserNo operator);

  @Mapping(source = "customerNo", target = "customerNo", qualifiedByName = "toCustomerNo")
  public abstract GetEntitlementQuery toQuery(GetEntitlementRequest request);

  @Named("toCustomerNo")
  protected CustomerNo toCustomerNo(String value) {
    return CustomerNo.of(value);
  }

  @Named("toAnnuityChannel")
  protected AnnuityChannel toAnnuityChannel(String channel) {
    return AnnuityChannel.valueOf(channel);
  }
}
