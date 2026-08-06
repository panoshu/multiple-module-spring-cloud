package com.pension.permission.infrastructure.role.mapper;

import com.mybatisflex.core.BaseMapper;
import com.pension.permission.infrastructure.role.entity.RoleTemplateDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色权限模板Mapper
 *
 * @author auth-service
 */
@Mapper
public interface RoleTemplateMapper extends BaseMapper<RoleTemplateDO> {
}
