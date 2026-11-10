package com.example.core.application.engine.service;

import com.example.core.application.engine.pipeline.StepPipelineExecutor;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.PipelineExecutionResult;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.config.ExtractorConfig;
import com.example.core.domain.engine.aggregate.valueobject.config.StepRouteConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.service.registry.BusinessFactExtractorRegistry;
import com.example.core.domain.engine.service.registry.StepActionHandlerRegistry;
import com.example.core.domain.engine.spi.BusinessFactExtractor;
import com.example.core.domain.engine.spi.StepActionHandler;
import com.example.core.types.ApplicationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

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

  private final StepActionHandlerRegistry handlerRegistry;
  private final BusinessFactExtractorRegistry factExtractorRegistry;

  private final BusinessConfigGateway configGateway;
  private final ApplicationRepository applicationRepository;
  private final StepPipelineExecutor pipelineExecutor;
  private final TransactionTemplate transactionTemplate;
  private final StepDefinitionResolver stepDefinitionResolver;
  private final StepRouteConfigAssembler stepRouteConfigAssembler;

  public void advanceStep(ApplicationId appId) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    BusinessMetaContext context = buildFullConfigContext(app);
    // 路由由版本化流程定义接管，不再直接查询过宽的 BusinessConfigGateway
    StepDefinition stepDefinition = stepDefinitionResolver.resolve(app);
    StepRouteConfig routeConfig = stepRouteConfigAssembler.toRouteConfig(stepDefinition);

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

    // 领域事件统一由 Repository 经 EventBus 发布（spec §13.2），应用服务不重复 publish
    transactionTemplate.executeWithoutResult(status -> applicationRepository.save(app));
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
      case APPROVAL_RESULT_REJECTED -> rejectApplication(appId);
      case APPROVAL_RESULT_WITHDRAWN -> withdrawApplication(appId);
      default -> throw new IllegalArgumentException("未知的审批结果类型: " + result);
    }
  }

  /**
   * 根据出站文件生成结果推进业务申请单。
   * <p>
   * 由 {@code OutboundFileEventListener} 在收到出站文件生成完成事件后调用。
   * 出站请求中的 {@code fileTaskId} 等于 {@code applicationId}，因此可直接加载申请单并推进。
   *
   * @param fileTaskId 文件任务 ID（等于 applicationId）
   */
  @Transactional
  public void advanceByOutboundFile(String fileTaskId) {
    ApplicationId appId = new ApplicationId(fileTaskId);
    advanceStep(appId);
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
