package com.example.approval.domain.aggregate.root;

import com.example.approval.types.ExecutionId;
import com.example.approval.types.RecordId;

/**
 * 审批 ID 生成端口（领域层定义，infrastructure 适配 shared-id 门面）.
 *
 * <p>背景：原聚合内 {@code RecordId.of(System.currentTimeMillis())} 自造 ID——
 * 同毫秒并发即碰撞（审批记录互相覆盖），且领域自造 ID 违背分层。
 * 本端口由应用层传入聚合方法，实现类
 * {@code IdServiceApprovalIdGenerator}（approval-infrastructure）
 * 委托 {@code IdService.nextId(...)}（shared-id-starter 雪花策略）.
 *
 * @author approval-domain
 */
public interface ApprovalIdGenerator {

  RecordId nextRecordId();

  ExecutionId nextExecutionId();
}
