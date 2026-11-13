package com.example.core.application.engine.dispatch;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.gateway.ApprovalIntegrationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * APPROVAL 类型的事务外派发处理器（spec §9.2 阶段 B / §12.2，批次 31）。
 * <p>
 * 审批启动参数（businessNo / businessType / 发起人等）均在 {@link BusinessApplication}
 * 内，事务外可从 {@link ApplicationRepository} 重建，无需持久化派发载荷；
 * requestNo 取协作记录。{@code startApproval} 已返回审批实例 ID（externalTaskId）。
 * 网关返回 null（未受理）时协作保持 DISPATCHING 交由恢复任务对账。
 */
@Component
@RequiredArgsConstructor
public class ApprovalDispatchHandler implements InteractionDispatchHandler {

  private final ApplicationRepository applicationRepository;
  private final ApprovalIntegrationGateway approvalGateway;

  @Override
  public InteractionType supportedType() {
    return InteractionType.APPROVAL;
  }

  @Override
  public String dispatch(ExternalInteraction interaction) {
    BusinessApplication app = applicationRepository.loadOrThrow(interaction.applicationId());
    return approvalGateway.startApproval(app, interaction.requestNo());
  }
}
