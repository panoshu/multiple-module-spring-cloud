package com.pension.permission.infrastructure.user.mapper;

import com.mybatisflex.core.BaseMapper;
import com.pension.permission.infrastructure.user.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper
 *
 * @author auth-service
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
