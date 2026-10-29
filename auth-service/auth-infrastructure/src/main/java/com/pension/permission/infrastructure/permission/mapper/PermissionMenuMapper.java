package com.pension.permission.infrastructure.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import com.pension.permission.infrastructure.permission.entity.PermissionMenuDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限菜单 Mapper
 *
 * @author auth-service
 */
@Mapper
public interface PermissionMenuMapper extends BaseMapper<PermissionMenuDO> {
}
