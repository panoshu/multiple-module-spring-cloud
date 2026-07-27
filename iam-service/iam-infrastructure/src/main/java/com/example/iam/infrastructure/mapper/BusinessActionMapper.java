package com.example.iam.infrastructure.mapper;

import com.example.iam.infrastructure.entity.BusinessActionDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务动作明细 Mapper。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper
public interface BusinessActionMapper extends BaseMapper<BusinessActionDO> {
}
