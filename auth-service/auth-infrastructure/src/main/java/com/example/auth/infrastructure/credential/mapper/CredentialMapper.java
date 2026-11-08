package com.example.auth.infrastructure.credential.mapper;

import com.mybatisflex.core.BaseMapper;
import com.example.auth.infrastructure.credential.entity.CredentialDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 凭证Mapper
 *
 * @author auth-service
 */
@Mapper
public interface CredentialMapper extends BaseMapper<CredentialDO> {
}
