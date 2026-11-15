package com.example.core.application.engine.callback;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionState;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import com.example.core.domain.engine.repository.StepExecutionRepository;
import com.example.core.types.ApplicationId;
import com.example.core.types.StepExecutionId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 外部协作回调关联服务（spec §7.3 / §9.2 阶段 C / §11.4 / §18）。
 * <p>
 * 入站集成事件携带 requestNo 时，优先按该键关联 {@link ExternalInteraction}，
 * 校验关联（matches）、协作类型与状态合法性后，在同一事务内闭环协作与步骤执行状态；
 * 乱序、类型不符、终态或找不到协作的事件按分类返回，绝不盲目推进流程。
 * <p>
 * 过渡期兼容：事件缺少 requestNo（旧构造，如演示环境 IntegrationEventSimulator）
 * 返回 {@link CorrelationClassification#NO_CORRELATION}，由监听器回退既有
 * fileTaskId / businessNo 关联路径，不改变既有行为。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackCorrelationService {

  private final ExternalInteractionRepository externalInteractionRepository;
  private final StepExecutionRepository stepExecutionRepository;

  /**
   * 关联并闭环一次外部协作回调。
   */
  @Transactional
  public CorrelationResult correlate(CallbackCommand command) {
    if (!hasText(command.requestNo())) {
      return CorrelationResult.noCorrelation();
    }

    Optional<ExternalInteraction> found = externalInteractionRepository
      .findByRequestNo(command.requestNo());
    if (found.isEmpty() && hasText(command.externalTaskId())) {
      found = externalInteractionRepository
        .findByExternalTaskId(command.expectedType(), command.externalTaskId());
    }
    if (found.isEmpty()) {
      log.warn("回调未匹配到外部协作, requestNo={}, externalTaskId={}（可对账）",
        command.requestNo(), command.externalTaskId());
      return CorrelationResult.unmatched();
    }

    ExternalInteraction interaction = found.get();
    ExternalInteractionStatus targetStatus = resolveTargetStatus(command.outcome());

    if (!interaction.matches(command.requestNo(), command.externalTaskId())
      || interaction.type() != command.expectedType()
      || interaction.status() == null
      || !interaction.status().canTransitTo(targetStatus)) {
      log.warn("回调关联校验不通过, requestNo={}, type={}, status={}, outcome={}（乱序/重复/串单）",
        command.requestNo(), interaction.type(), interaction.status(), command.outcome());
      return CorrelationResult.outOfOrder();
    }

    StepExecution execution = loadExecution(interaction.stepExecutionId());
    applyOutcome(interaction, execution, command, targetStatus);

    externalInteractionRepository.save(interaction);
    if (execution != null) {
      stepExecutionRepository.save(execution);
    }
    ApplicationId applicationId = interaction.applicationId();
    return CorrelationResult.applied(applicationId);
  }

  /**
   * 回调结果 → 协作目标状态的唯一映射（spec §7.3 / §21.3，批次 42）。
   * <p>
   * 仅 {@code SUCCEEDED} 视为成功；{@code FAILED} / {@code REJECTED} / {@code WITHDRAWN}
   * 与结果缺失均按 {@code FAILED} 闭环。此前「非 FAILED 即 SUCCEEDED」会把审批驳回/撤回
   * 静默写成成功，导致协作与步骤执行为 SUCCEEDED 而申请单已被终止，审计状态互相矛盾。
   * switch 穷尽全部枚举常量，未来新增结果必须在此显式定义映射。
   */
  private ExternalInteractionStatus resolveTargetStatus(CallbackOutcome outcome) {
    if (outcome == null) {
      return ExternalInteractionStatus.FAILED;
    }
    return switch (outcome) {
      case SUCCEEDED -> ExternalInteractionStatus.SUCCEEDED;
      case FAILED, REJECTED, WITHDRAWN -> ExternalInteractionStatus.FAILED;
    };
  }

  private StepExecution loadExecution(StepExecutionId stepExecutionId) {
    if (stepExecutionId == null) {
      return null;
    }
    return stepExecutionRepository.load(stepExecutionId).orElse(null);
  }

  private void applyOutcome(ExternalInteraction interaction, StepExecution execution,
                            CallbackCommand command, ExternalInteractionStatus targetStatus) {
    String operator = command.operator();
    if (targetStatus == ExternalInteractionStatus.FAILED) {
      interaction.fail(command.resultRef(), operator);
      if (execution != null && execution.state().canTransitTo(StepExecutionState.FAILED)) {
        execution.fail("EXTERNAL_CALLBACK_FAILED", command.resultRef(), operator);
      }
    } else {
      interaction.succeed(command.resultRef(), operator);
      if (execution != null && execution.state().canTransitTo(StepExecutionState.SUCCEEDED)) {
        execution.succeed(operator);
      }
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
