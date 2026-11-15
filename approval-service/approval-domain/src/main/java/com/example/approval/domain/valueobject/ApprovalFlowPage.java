package com.example.approval.domain.valueobject;

import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;

/**
 * 审批流条件分页查询结果（批次 54，P3-1 能力 B）。
 * <p>
 * 领域层的查询投影：总数与当前页聚合根列表，供应用层组装 {@code PageData}。
 * 仓储负责 DB 侧计数与 limit，避免全表加载后内存分页。
 *
 * @param totalCount 满足条件的总数
 * @param items      当前页的审批流聚合根
 * @author approval-service
 */
public record ApprovalFlowPage(int totalCount, List<ApprovalFlow> items) implements ValueObject {

  public static ApprovalFlowPage empty() {
    return new ApprovalFlowPage(0, List.of());
  }
}
