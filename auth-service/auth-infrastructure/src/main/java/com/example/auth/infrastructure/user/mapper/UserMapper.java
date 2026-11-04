package com.example.auth.infrastructure.user.mapper;

import com.mybatisflex.core.BaseMapper;
import com.example.auth.infrastructure.user.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper
 *
 * @author auth-service
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
