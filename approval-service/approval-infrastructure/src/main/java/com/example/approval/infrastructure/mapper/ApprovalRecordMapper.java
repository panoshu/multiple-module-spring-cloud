package com.example.approval.infrastructure.mapper;

import com.example.approval.infrastructure.entity.ApprovalRecordDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批记录Mapper
 *
 * @author approval-service
 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecordDO> {
}