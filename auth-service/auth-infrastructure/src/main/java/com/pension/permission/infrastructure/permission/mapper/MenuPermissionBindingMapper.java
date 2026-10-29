package com.pension.permission.infrastructure.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import com.pension.permission.infrastructure.permission.entity.MenuPermissionBindingDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 菜单-权限绑定 Mapper
 *
 * @author auth-service
 */
@Mapper
public interface MenuPermissionBindingMapper extends BaseMapper<MenuPermissionBindingDO> {
}
