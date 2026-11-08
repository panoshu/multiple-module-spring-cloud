package com.example.auth.infrastructure.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import com.example.auth.infrastructure.permission.entity.PermissionMenuDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限菜单 Mapper
 *
 * @author auth-service
 */
@Mapper
public interface PermissionMenuMapper extends BaseMapper<PermissionMenuDO> {
}
