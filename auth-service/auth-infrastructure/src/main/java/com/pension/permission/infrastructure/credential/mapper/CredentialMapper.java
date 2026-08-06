package com.pension.permission.infrastructure.credential.mapper;

import com.mybatisflex.core.BaseMapper;
import com.pension.permission.infrastructure.credential.entity.CredentialDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 凭证Mapper
 *
 * @author auth-service
 */
@Mapper
public interface CredentialMapper extends BaseMapper<CredentialDO> {
}
