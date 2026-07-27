package com.example.iam.infrastructure.mapper;

import com.example.iam.infrastructure.entity.UserProfileDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户渠道专属档案 Mapper。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfileDO> {
}
