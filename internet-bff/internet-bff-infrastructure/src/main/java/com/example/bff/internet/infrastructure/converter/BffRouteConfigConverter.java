package com.example.bff.internet.infrastructure.converter;

import com.example.bff.internet.infrastructure.entity.BffRouteConfigDO;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.ChannelScope;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * BFF 路由配置领域对象与 DO 转换器
 *
 * @author bff
 */
@Mapper(componentModel = "spring")
public interface BffRouteConfigConverter {

  @Mapping(source = "channelScope", target = "channelScope", qualifiedByName = "toString")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "enabled", ignore = true)
  @Mapping(target = "description", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createTime", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updateTime", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "version", ignore = true)
  BffRouteConfigDO toDO(BffRouteConfig config);

  @Mapping(source = "channelScope", target = "channelScope", qualifiedByName = "toChannelScope")
  BffRouteConfig toDomain(BffRouteConfigDO record);

  @Named("toString")
  static String channelScopeToString(ChannelScope scope) {
    return scope.name();
  }

  @Named("toChannelScope")
  static ChannelScope stringToChannelScope(String value) {
    return ChannelScope.valueOf(value);
  }
}
