package com.pension.permission.infrastructure.assignment.mapper;

import com.mybatisflex.core.BaseMapper;
import com.pension.permission.infrastructure.assignment.entity.AssignmentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账号身份分配Mapper
 *
 * @author auth-service
 */
@Mapper
public interface AssignmentMapper extends BaseMapper<AssignmentDO> {
}
