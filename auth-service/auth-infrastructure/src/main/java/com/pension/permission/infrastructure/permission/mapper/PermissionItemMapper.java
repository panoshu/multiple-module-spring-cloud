package com.pension.permission.infrastructure.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import com.pension.permission.infrastructure.permission.entity.PermissionItemDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限点元数据 Mapper
 *
 * @author auth-service
 */
@Mapper
public interface PermissionItemMapper extends BaseMapper<PermissionItemDO> {
}
