package com.example.iam.infrastructure.mapper;

import com.example.iam.infrastructure.entity.LoginLogDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLogDO> {
}
