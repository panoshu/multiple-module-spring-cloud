package com.example.iam.infrastructure.mapper;

import com.example.iam.infrastructure.entity.SecondaryAuthSessionDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 二次授权会话 Mapper。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper
public interface SecondaryAuthSessionMapper extends BaseMapper<SecondaryAuthSessionDO> {
}
