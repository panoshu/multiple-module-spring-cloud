package com.example.approval.infrastructure.mapper;

import com.example.approval.infrastructure.entity.ApprovalNodeExecutionDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 节点执行记录Mapper
 *
 * @author approval-service
 */
@Mapper
public interface ApprovalNodeExecutionMapper extends BaseMapper<ApprovalNodeExecutionDO> {
}