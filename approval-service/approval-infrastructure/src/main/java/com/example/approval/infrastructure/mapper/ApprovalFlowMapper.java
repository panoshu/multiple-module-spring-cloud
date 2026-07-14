package com.example.approval.infrastructure.mapper;

import com.example.approval.infrastructure.entity.ApprovalFlowDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批流Mapper
 *
 * @author approval-service
 */
@Mapper
public interface ApprovalFlowMapper extends BaseMapper<ApprovalFlowDO> {
}