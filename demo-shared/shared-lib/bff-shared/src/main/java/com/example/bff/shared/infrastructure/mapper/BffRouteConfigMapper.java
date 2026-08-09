package com.example.bff.shared.infrastructure.mapper;

import com.example.bff.shared.infrastructure.entity.BffRouteConfigDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * BFF 路由配置 Mapper
 *
 * @author bff
 */
@Mapper
public interface BffRouteConfigMapper extends BaseMapper<BffRouteConfigDO> {
}