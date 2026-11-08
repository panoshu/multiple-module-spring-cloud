package com.example.auth.infrastructure.authorization.mapper;

import com.mybatisflex.core.BaseMapper;
import com.example.auth.infrastructure.authorization.entity.GrantDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 授权策略主记录 Mapper.
 *
 * @author auth-service
 */
@Mapper
public interface GrantMapper extends BaseMapper<GrantDO> {
}
