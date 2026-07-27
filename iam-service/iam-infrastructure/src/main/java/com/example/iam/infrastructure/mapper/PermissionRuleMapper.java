package com.example.iam.infrastructure.mapper;

import com.example.iam.infrastructure.entity.PermissionRuleDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限规则 Mapper。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper
public interface PermissionRuleMapper extends BaseMapper<PermissionRuleDO> {
}
