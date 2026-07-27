package com.example.iam.infrastructure.mapper;

import com.example.iam.infrastructure.entity.PlanDelegationPermissionDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代办授权权限明细 Mapper。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper
public interface PlanDelegationPermissionMapper extends BaseMapper<PlanDelegationPermissionDO> {
}
