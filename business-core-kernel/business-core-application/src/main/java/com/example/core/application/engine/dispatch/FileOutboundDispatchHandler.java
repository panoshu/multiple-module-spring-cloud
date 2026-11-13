package com.example.core.application.engine.dispatch;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.domain.engine.repository.StepExecutionRepository;
import com.example.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * FILE_OUTBOUND 类型的事务外派发处理器（spec §9.2 阶段 B，批次 30）。
 * <p>
 * 出站凭证由批次 30 的登记意图阶段持久化到 {@code StepExecution.checkpoint_ref}
 * （{@link OutboundDispatchPayload} 紧凑编码），本处理器按
 * {@code interaction.stepExecutionId()} 加载步骤执行、解码还原凭证后调用
 * {@code triggerOutboundGenerationAndGetTaskId}。载荷缺失或格式错误时返回 null，
 * 协作保持 DISPATCHING 交由恢复任务对账，绝不盲目派发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileOutboundDispatchHandler implements InteractionDispatchHandler {

  private static final String OPERATOR = "SYSTEM";

  private final StepExecutionRepository stepExecutionRepository;
  private final FileIntegrationGateway fileIntegrationGateway;

  @Override
  public InteractionType supportedType() {
    return InteractionType.FILE_OUTBOUND;
  }

  @Override
  public String dispatch(ExternalInteraction interaction) {
    StepExecution execution = stepExecutionRepository.load(interaction.stepExecutionId())
      .orElse(null);
    if (execution == null || execution.checkpointRef() == null
      || execution.checkpointRef().isBlank()) {
      log.warn("出站派发载荷缺失，FILE_OUTBOUND 协作保持 DISPATCHING 待对账: interactionId={}, requestNo={}",
        interaction.id(), interaction.requestNo());
      return null;
    }
    OutboundDispatchPayload payload;
    try {
      payload = OutboundDispatchPayload.decode(execution.checkpointRef());
    } catch (DomainException e) {
      log.error("出站派发载荷格式错误，FILE_OUTBOUND 协作保持 DISPATCHING 待对账: interactionId={}",
        interaction.id(), e);
      return null;
    }
    return fileIntegrationGateway.triggerOutboundGenerationAndGetTaskId(
      payload.fileTaskId(), payload.targetTemplateId(), Map.of(), OPERATOR,
      interaction.requestNo());
  }
}
