package com.example.auth.infrastructure.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import com.example.auth.infrastructure.permission.entity.PermissionItemDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限点元数据 Mapper
 *
 * @author auth-service
 */
@Mapper
public interface PermissionItemMapper extends BaseMapper<PermissionItemDO> {
}
