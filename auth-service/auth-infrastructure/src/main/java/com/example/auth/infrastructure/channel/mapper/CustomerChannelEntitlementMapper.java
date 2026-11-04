package com.example.auth.infrastructure.channel.mapper;

import com.mybatisflex.core.BaseMapper;
import com.example.auth.infrastructure.channel.entity.CustomerChannelEntitlementDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户渠道开通记录 Mapper.
 */
@Mapper
public interface CustomerChannelEntitlementMapper
  extends BaseMapper<CustomerChannelEntitlementDO> {
}
