package com.example.approval.infrastructure.mapper;

import com.example.approval.infrastructure.entity.ApprovalInstanceDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批实例Mapper
 *
 * @author approval-service
 */
@Mapper
public interface ApprovalInstanceMapper extends BaseMapper<ApprovalInstanceDO> {
}