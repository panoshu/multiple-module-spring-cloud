package com.example.iam.infrastructure.mapper;

import com.example.iam.infrastructure.entity.UserDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * IAM 用户主表 Mapper。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
