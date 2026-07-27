package com.example.iam.infrastructure.mapper;

import com.example.iam.infrastructure.entity.CredentialDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 凭据 Mapper。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper
public interface CredentialMapper extends BaseMapper<CredentialDO> {
}
