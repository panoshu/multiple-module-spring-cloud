package com.example.auth.infrastructure.role.mapper;

import com.mybatisflex.core.BaseMapper;
import com.example.auth.infrastructure.role.entity.RoleVisibilityDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色可见性范围Mapper
 *
 * @author auth-service
 */
@Mapper
public interface RoleVisibilityMapper extends BaseMapper<RoleVisibilityDO> {
}
