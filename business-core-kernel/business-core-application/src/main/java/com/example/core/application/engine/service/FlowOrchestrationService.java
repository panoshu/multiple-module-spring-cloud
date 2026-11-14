package com.example.core.application.engine.service;

import com.example.core.api.application.command.AdvanceStepCommand;
import com.example.core.application.engine.pipeline.StepPipelineExecutor;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.business.aggregate.valueobject.enums.status.ApplicationStatus;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import com.example.core.types.ExternalInteractionId;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.PipelineExecutionResult;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.config.ExtractorConfig;
import com.example.core.domain.engine.aggregate.valueobject.config.StepRouteConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.repository.StepExecutionRepository;
import com.example.core.domain.engine.service.registry.BusinessFactExtractorRegistry;
import com.example.core.domain.engine.service.registry.StepActionHandlerRegistry;
import com.example.core.domain.engine.spi.BusinessFactExtractor;
import com.example.core.domain.engine.spi.StepActionHandler;
import com.example.core.types.ApplicationId;
import com.example.core.types.StepExecutionId;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 应用层编排服务
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 16:48
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowOrchestrationService {

  private static final String APPROVAL_RESULT_APPROVED = "APPROVED";
  private static final String APPROVAL_RESULT_REJECTED = "REJECTED";
  private static final String APPROVAL_RESULT_WITHDRAWN = "WITHDRAWN";
  private static final String LOCK_KEY_PREFIX = "kernel:application:";
  private static final long LOCK_WAIT_SECONDS = 3L;
  private static final long LOCK_LEASE_SECONDS = 10L;

  /** §14 建议统一携带的 MDC 键；traceId 由 shared-web-starter 的链路过滤器注入。 */
  private static final String MDC_APPLICATION_ID = "applicationId";
  private static final String MDC_STEP_EXECUTION_ID = "stepExecutionId";
  private static final String MDC_REQUEST_NO = "requestNo";
  private static final String MDC_BUSINESS_TYPE = "businessType";

  private final StepActionHandlerRegistry handlerRegistry;
  private final BusinessFactExtractorRegistry factExtractorRegistry;

  private final BusinessConfigGateway configGateway;
  private final ApplicationRepository applicationRepository;
  private final StepPipelineExecutor pipelineExecutor;
  private final TransactionTemplate transactionTemplate;
  private final StepDefinitionResolver stepDefinitionResolver;
  private final StepRouteConfigAssembler stepRouteConfigAssembler;
  private final StepExecutionRepository stepExecutionRepository;
  private final IdService idService;
  private final ExternalInteractionRepository externalInteractionRepository;
  private final ObjectProvider<DistributedLock> lockProvider;

  public void advanceStep(ApplicationId appId) {
    withApplicationLock(appId, () -> {
      doAdvanceStep(appId, null, null);
      return null;
    });
  }

  /**
   * 按 CQE 推进申请单（spec §15.1，批次 35）。
   * <p>
   * 外部调用方携带 expectedStep 与 requestNo：期望步骤不符时拒绝，防止过期请求推进；
   * 同一 requestNo 已存在步骤执行记录则直接跳过，保证重复提交只执行一次。
   * requestNo 由 Command 提供，不再仅由编排器内部派生；operator 为空时回退到申请单上下文。
   */
  public void advanceApplication(AdvanceStepCommand command) {
    if (command == null || !StringUtils.hasText(command.applicationId())
      || !StringUtils.hasText(command.expectedStep()) || !StringUtils.hasText(command.requestNo())) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("推进申请单命令缺少 applicationId/expectedStep/requestNo");
    }
    ApplicationId appId = new ApplicationId(command.applicationId());
    withApplicationLock(appId, () -> {
      if (stepExecutionRepository.findByRequestNo(command.requestNo()).isPresent()) {
        log.info("重复推进请求已跳过: requestNo={}", command.requestNo());
        return null;
      }
      BusinessApplication app = applicationRepository.loadOrThrow(appId);
      if (app.currentStep() == null
        || !command.expectedStep().equals(app.currentStep().name())) {
        throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
          .withLogDetail("期望步骤与当前步骤不符: expected=%s, current=%s, requestNo=%s"
            .formatted(command.expectedStep(),
              app.currentStep() == null ? null : app.currentStep().name(),
              command.requestNo()));
      }
      doAdvanceStep(appId, command.requestNo(), command.operator());
      return null;
    });
  }

  /**
   * 流程推进短临界区互斥（spec §9.3 第一层）。
   * <p>
   * 弱依赖：宿主未引入 shared-lock-starter 时无锁直通，由乐观锁与唯一约束兜底；
   * 锁等待 3s、租期 10s 且无看门狗，绝不覆盖远程 IO 或长批量处理。
   */
  private <T> T withApplicationLock(ApplicationId appId, Supplier<T> task) {
    Map<String, String> previousMdc = MDC.getCopyOfContextMap();
    MDC.put(MDC_APPLICATION_ID, appId.value());
    try {
      DistributedLock lock = lockProvider.getIfAvailable();
      if (lock == null) {
        return task.get();
      }
      return lock.lockAndRun(LOCK_KEY_PREFIX + appId.value(),
        LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS, task);
    } finally {
      if (previousMdc == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(previousMdc);
      }
    }
  }

  private void doAdvanceStep(ApplicationId appId, String requestNoOverride, String operatorOverride) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    BusinessMetaContext context = buildFullConfigContext(app);
    // 路由由版本化流程定义接管，不再直接查询过宽的 BusinessConfigGateway
    StepDefinition stepDefinition = stepDefinitionResolver.resolve(app);
    StepRouteConfig routeConfig = stepRouteConfigAssembler.toRouteConfig(stepDefinition);

    // 每次步骤尝试独立建记录，不把执行历史塞进 BusinessApplication（§7.2）
    String operator = StringUtils.hasText(operatorOverride) ? operatorOverride : operatorOf(app);
    StepExecution execution = newStepExecution(appId, app, stepDefinition, requestNoOverride);
    execution.start(operator);

    // §14：推进期间统一携带流程标识，供日志/链路追踪串联；临界区结束后恢复现场
    MDC.put(MDC_STEP_EXECUTION_ID, String.valueOf(execution.id().value()));
    MDC.put(MDC_REQUEST_NO, "SE-" + execution.id().value());
    if (context.businessType() != null) {
      MDC.put(MDC_BUSINESS_TYPE, context.businessType().name());
    }

    // 外部协作幂等键与 ExternalInteraction.requestNo 同源（SE-<步骤执行ID>），
    // 经上下文传给挂起 Handler 透传到 file/approval（§7.3 / §11.4 / §12.3）
    context.extensionFacts().put(BusinessMetaContext.REQUEST_NO_FACT,
      "SE-" + execution.id().value());

    // 【删除】之前对 isUserActionCompletedForCurrentStep() 的判断
    // 现在的逻辑：既然你能调到 advanceStep，说明是外部手动触发的(或者SystemTask自动触发的)。
    // 到底业务数据填没填好？全靠下面的 preExtensions 里面的业务校验器来拦截！

    // 1. 执行前置管道 (如果是用户任务，这里必须配置状态校验器)
    // 比如配置了 "materialCompletenessValidator"，它内部会调用 app.isPlanMaterialSatisfied()
    // 如果校验不通过，直接抛出业务异常，事务回滚，提示前端。
    PipelineExecutionResult preResult = pipelineExecutor.executeChain(app, context, routeConfig.preValidations());
    app.recordPipelineExecution("PRE_VALIDATION", preResult);

    // 2. 执行核心处理器
    StepExecutionStatus executionStatus = StepExecutionStatus.SUCCESS;
    if (StringUtils.hasText(routeConfig.mainProcessor())) {
      StepActionHandler handler = handlerRegistry.get(routeConfig.mainProcessor());
      executionStatus = handler.execute(app, context);
    }

    // 3. 执行明细层处理与后置处理
    if (executionStatus == StepExecutionStatus.SUCCESS) {
      // 执行明细层处理
      PipelineExecutionResult postResult = pipelineExecutor.executeChain(app, context, routeConfig.detailProcessors());
      app.recordPipelineExecution("DETAIL_PROCESSOR", postResult);

      // 执行副作用 (如异步通知)
      postResult = pipelineExecutor.executeChain(app, context, routeConfig.sideEffects());
      app.recordPipelineExecution("SIDE_EFFECTS", postResult);

      app.completeCurrentStep(routeConfig.nextStep());

    } else {
      log.info("流程挂起，物理状态: {}", executionStatus);
    }

    // 领域事件统一由 Repository 经 EventBus 发布（spec §13.2），应用服务不重复 publish；
    // 申请单状态、步骤执行记录与 Outbox 在同一本地事务保存（spec §9.1）
    StepExecutionStatus finalStatus = executionStatus;
    String mainProcessor = routeConfig.mainProcessor();
    // 挂起 Handler 登记的事务外派发载荷：写入步骤执行检查点，供派发器事务外重建参数（§9.2 阶段 B）
    String dispatchPayload = finalStatus == StepExecutionStatus.SUSPEND_ASYNC_WAIT
      ? context.dispatchPayload() : null;
    transactionTemplate.executeWithoutResult(status -> {
      // 挂起登记阶段即把申请单置 WAITING_EXTERNAL（§8.1），与状态/步骤/协作同事务保存（§9.1）。
      // 外部受理后派发器再把协作推进 WAITING_CALLBACK；回调经 completeWaitingStep 归位 RUNNING。
      if (finalStatus == StepExecutionStatus.SUSPEND_ASYNC_WAIT) {
        app.waitForExternal();
      }
      applicationRepository.save(app);
      applyHandlerResult(execution, finalStatus, operator);
      if (dispatchPayload != null) {
        execution.recordDispatchPayload(dispatchPayload, operator);
      }
      stepExecutionRepository.save(execution);
      if (finalStatus == StepExecutionStatus.SUSPEND_ASYNC_WAIT) {
        ExternalInteraction interaction = newExternalInteraction(appId, execution, mainProcessor, operator);
        if (interaction != null) {
          externalInteractionRepository.save(interaction);
        }
      }
    });
  }

  /**
   * 过渡期按主处理器名推断外部协作类型；独立定义存储落地后应改由
   * StepDefinition 显式声明 ActionType（§7.1 / §14）。
   */
  private InteractionType interactionTypeOf(String mainProcessor) {
    if (mainProcessor == null) {
      return null;
    }
    String name = mainProcessor.toLowerCase();
    if (name.contains("approval")) {
      return InteractionType.APPROVAL;
    }
    if (name.contains("outbound") || name.contains("archive")) {
      return InteractionType.FILE_OUTBOUND;
    }
    if (name.contains("formparsing") || name.contains("form_parse")) {
      return InteractionType.FILE_PARSE;
    }
    return null;
  }

  private ExternalInteraction newExternalInteraction(ApplicationId appId, StepExecution execution,
                                                     String mainProcessor, String operator) {
    InteractionType type = interactionTypeOf(mainProcessor);
    if (type == null) {
      return null;
    }
    return ExternalInteraction.create(
      idService.nextLongId(ExternalInteractionId.class, "EXTERNAL_INTERACTION"),
      appId, execution.id(), type, operator);
  }

  private StepExecution newStepExecution(ApplicationId appId, BusinessApplication app,
                                         StepDefinition stepDefinition, String requestNoOverride) {
    StepCode stepCode = stepDefinition.stepCode();
    List<StepExecution> history = stepExecutionRepository.findByApplicationAndStep(appId, stepCode);
    int attempt = (history == null ? 0 : history.size()) + 1;
    String requestNo = StringUtils.hasText(requestNoOverride)
      ? requestNoOverride : "%s:%s:%d".formatted(appId.value(), stepCode.value(), attempt);
    DefinitionVersion version = app.definitionVersion() != null
      ? app.definitionVersion() : DefinitionVersion.initial();
    return StepExecution.create(
      idService.nextLongId(StepExecutionId.class, "STEP_EXECUTION"),
      appId, version, stepCode, attempt, requestNo, operatorOf(app));
  }

  /**
   * §8.2 唯一的结果→状态转换点：Handler 只返回领域结果（{@code StepExecutionStatus}），
   * 由应用服务在此迁移 {@code StepExecutionState} 八态状态机；不得把返回值直接写入聚合，
   * 也不得让 Handler 自行决定持久化状态。
   */
  private void applyHandlerResult(StepExecution execution, StepExecutionStatus status, String operator) {
    switch (status) {
      case SUCCESS -> execution.succeed(operator);
      case SUSPEND_ASYNC_WAIT -> execution.waitForExternal(operator);
      case FAILED -> execution.fail("STEP_FAILED", "步骤执行失败", operator);
    }
  }

  private String operatorOf(BusinessApplication app) {
    return app.operatorInfo() != null ? app.operatorInfo().operatorId() : "SYSTEM";
  }

  /**
   * 按版本化流程定义判断申请单当前步骤是否为系统任务。
   * 供 {@code StepAutoAdvanceListener} 在事务提交后决定是否自动推进。
   */
  public boolean isSystemTask(ApplicationId appId) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    return stepDefinitionResolver.isSystemTask(app);
  }

  /**
   * 根据文件任务 ID 反查业务申请单并推进流程。
   * <p>
   * 由 {@code FileParsedEventListener} 在收到文件解析完成事件后调用。
   * 通过 {@link ApplicationRepository#findByFileTaskId(String)} 反查申请单，
   * 命中后复用 {@link #advanceStep(ApplicationId)} 推进至下一节点。
   *
   * @param fileTaskId 文件任务 ID（来自 {@code FileParsedEventDTO.fileTaskId()}）
   */
  @Transactional
  public void advanceByFileTaskId(String fileTaskId) {
    Optional<BusinessApplication> appOpt = applicationRepository.findByFileTaskId(fileTaskId);
    if (appOpt.isEmpty()) {
      log.warn("未找到 fileTaskId={} 对应的业务申请单，忽略文件解析完成事件", fileTaskId);
      return;
    }
    advanceStep(appOpt.get().id());
  }

  /**
   * 根据审批结果推进或终止业务申请单。
   * <p>
   * 由 {@code ApprovalResultEventListener} 在收到审批结果事件后调用。
   * <ul>
   * <li>{@code APPROVED}：推进到下一个流程节点（复用 {@link #advanceStep(ApplicationId)}）；</li>
   * <li>{@code REJECTED}：将申请单状态置为 REJECTED；</li>
   * <li>{@code WITHDRAWN}：将申请单状态置为 WITHDRAWN。</li>
   * </ul>
   *
   * @param businessNo 业务编号（即 {@code ApplicationId.value()}，来自审批事件的 businessNo 字段）
   * @param result     审批结果字符串，取值为 APPROVED / REJECTED / WITHDRAWN
   */
  @Transactional
  public void advanceByApprovalResult(String businessNo, String result) {
    ApplicationId appId = new ApplicationId(businessNo);
    switch (result) {
      case APPROVAL_RESULT_APPROVED -> advanceStep(appId);
      case APPROVAL_RESULT_REJECTED -> withApplicationLock(appId, () -> {
        rejectApplication(appId);
        return null;
      });
      case APPROVAL_RESULT_WITHDRAWN -> withApplicationLock(appId, () -> {
        withdrawApplication(appId);
        return null;
      });
      default -> throw new IllegalArgumentException("未知的审批结果类型: " + result);
    }
  }

  /**
   * 完成等待中的外部步骤（spec §9.2 阶段 C）。
   * <p>
   * 外部协作回调到达后，用本门面把流程从 WAITING_EXTERNAL 归位 RUNNING 并按版本化
   * 定义推进到 nextStep；与 {@link #advanceStep(ApplicationId)} 不同，本方法
   * <b>不重跑 Handler 管线、不新建步骤执行或协作记录、不重复触发外部调用</b>——
   * 这些副作用在挂起时已经发生，回调只负责推进流程实例。
   * <p>
   * 仅接受 WAITING_EXTERNAL / RUNNING 在途态；终态、FAILED、WAITING_USER 及后继为空
   * 均不推进（由调用方分类处理），避免乱序回调盲目跃迁。
   *
   * @param appId 关联到的权威申请单 ID（来自 ExternalInteraction）
   */
  @Transactional
  public void completeWaitingStep(ApplicationId appId) {
    withApplicationLock(appId, () -> {
      doCompleteWaitingStep(appId);
      return null;
    });
  }

  private void doCompleteWaitingStep(ApplicationId appId) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    ApplicationStatus status = app.status();
    if (status == null || (status != ApplicationStatus.WAITING_EXTERNAL
      && status != ApplicationStatus.RUNNING)) {
      log.warn("完成等待步骤：申请单非在途等待态，不推进, appId={}, status={}", appId, status);
      return;
    }
    StepDefinition stepDefinition = stepDefinitionResolver.resolve(app);
    StepRouteConfig routeConfig = stepRouteConfigAssembler.toRouteConfig(stepDefinition);
    if (routeConfig.nextStep() == null) {
      log.warn("完成等待步骤：后继为空，不推进, appId={}, step={}", appId, app.currentStep());
      return;
    }
    if (status == ApplicationStatus.WAITING_EXTERNAL) {
      app.retry(); // WAITING_EXTERNAL → RUNNING（合法迁移，§8.1）
    }
    app.completeCurrentStep(routeConfig.nextStep());
    applicationRepository.save(app);
  }

  private void rejectApplication(ApplicationId appId) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    app.reject();
    saveApplication(app);
  }

  private void withdrawApplication(ApplicationId appId) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    app.withdraw();
    saveApplication(app);
  }

  private void saveApplication(BusinessApplication app) {
    applicationRepository.save(app);
  }

  // ==========================================
  // 核心私有方法：两阶段构建配置查询上下文
  // ==========================================
  private BusinessMetaContext buildFullConfigContext(BusinessApplication app) {
    // 阶段1：构建基础上下文 (此时没有扩展事实，只有客户、产品等基础维度)
    BusinessMetaContext baseContext = app.buildConfigQueryContext();

    // 阶段2：独立查询提取器配置
    ExtractorConfig extractorConfig = configGateway.getExtractorConfig(baseContext);

    // 执行提取器，获取业务事实
    Map<String, Object> facts = Collections.emptyMap();
    if (extractorConfig != null && extractorConfig.extractorName() != null) {
      BusinessFactExtractor extractor = factExtractorRegistry.get(extractorConfig.extractorName());
      facts = extractor.extractBusinessFacts(app);
    }

    // 阶段3：构建包含完整事实的上下文返回
    return BusinessMetaContext.withExtensionFacts(baseContext, facts);
  }
}
