package com.example.auth.adapter.converter;

import com.example.auth.api.command.*;
import com.example.auth.api.query.GetEntitlementQuery;
import com.example.auth.api.query.GetEntitlementRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 客户渠道开通记录 Adapter 层转换器.
 *
 * <p>负责请求体 DTO 与 Command/Query 之间的转换。AU-02 后 auth-api 不再依赖
 * business-core-types，Command 使用 String 传输，应用服务在边界再包装强类型。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class CustomerChannelEntitlementConverter {

  @Mapping(source = "request.customerNo", target = "customerNo")
  @Mapping(source = "request.channelType", target = "channel")
  @Mapping(source = "operator", target = "operator")
  public abstract EnableChannelCommand toCommand(EnableChannelRequest request, String operator);

  @Mapping(source = "request.customerNo", target = "customerNo")
  @Mapping(source = "request.channelType", target = "channel")
  @Mapping(source = "operator", target = "operator")
  public abstract DisableChannelCommand toCommand(DisableChannelRequest request, String operator);

  @Mapping(source = "request.customerNo", target = "customerNo")
  @Mapping(source = "request.channelTypes", target = "channels")
  @Mapping(source = "operator", target = "operator")
  public abstract ReplaceChannelsCommand toCommand(ReplaceChannelsRequest request, String operator);

  public abstract GetEntitlementQuery toQuery(GetEntitlementRequest request);
}
