package com.pension.permission.infrastructure.channel.mapper;

import com.mybatisflex.core.BaseMapper;
import com.pension.permission.infrastructure.channel.entity.SecondaryAuthSessionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 二次授权会话Mapper
 *
 * @author auth-service
 */
@Mapper
public interface SecondaryAuthSessionMapper extends BaseMapper<SecondaryAuthSessionDO> {
}
