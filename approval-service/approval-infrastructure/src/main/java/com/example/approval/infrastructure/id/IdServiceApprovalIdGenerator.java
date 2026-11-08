package com.example.approval.infrastructure.id;

import com.example.approval.domain.aggregate.root.ApprovalIdGenerator;
import com.example.approval.types.ExecutionId;
import com.example.approval.types.RecordId;
import com.example.shared.identifier.contract.IdService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 审批 ID 生成端口适配器：委托 shared-id-starter 门面（雪花策略）.
 *
 * <p>领域层只见 {@link ApprovalIdGenerator} 端口（零框架依赖），
 * ID 策略切换（SNOWFLAKE→SEGMENT 等）只改 @IdDefinition 注解，本适配器零改动.
 *
 * @author approval-infrastructure
 */
@Component
@RequiredArgsConstructor
public class IdServiceApprovalIdGenerator implements ApprovalIdGenerator {

  private final IdService idService;

  @Override
  public RecordId nextRecordId() {
    return idService.nextId(RecordId.class);
  }

  @Override
  public ExecutionId nextExecutionId() {
    return idService.nextId(ExecutionId.class);
  }
}
