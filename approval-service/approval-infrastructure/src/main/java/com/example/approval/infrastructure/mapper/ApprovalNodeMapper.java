package com.example.approval.infrastructure.mapper;

import com.example.approval.infrastructure.entity.ApprovalNodeDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批节点Mapper
 *
 * @author approval-service
 */
@Mapper
public interface ApprovalNodeMapper extends BaseMapper<ApprovalNodeDO> {
}