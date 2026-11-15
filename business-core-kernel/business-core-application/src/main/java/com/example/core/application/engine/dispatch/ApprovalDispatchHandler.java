package com.example.core.application.engine.dispatch;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.gateway.ApprovalIntegrationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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
  /**
   * 审批网关为可选依赖：不承载审批的服务可不装配（core.integration.approval.enabled=false），
   * 启动不失败；真正派发审批时给出明确错误，而非 NPE。
   */
  private final ObjectProvider<ApprovalIntegrationGateway> approvalGatewayProvider;

  @Override
  public InteractionType supportedType() {
    return InteractionType.APPROVAL;
  }

  @Override
  public String dispatch(ExternalInteraction interaction) {
    ApprovalIntegrationGateway approvalGateway = approvalGatewayProvider.getIfAvailable();
    if (approvalGateway == null) {
      throw new IllegalStateException(
        "审批集成网关未装配（core.integration.approval.enabled=false 或缺 approval-api 客户端），"
          + "无法派发审批协作: requestNo=" + interaction.requestNo());
    }
    BusinessApplication app = applicationRepository.loadOrThrow(interaction.applicationId());
    return approvalGateway.startApproval(app, interaction.requestNo());
  }
}
