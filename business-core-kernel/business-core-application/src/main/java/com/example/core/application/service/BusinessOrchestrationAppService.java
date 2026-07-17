package com.example.core.application.service;

import com.example.core.application.pipeline.StepPipelineExecutor;
import com.example.core.domain.gateway.BusinessConfigGateway;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.vauleobject.BusinessMetaContext;
import com.example.core.domain.aggregate.vauleobject.PipelineExecutionResult;
import com.example.core.domain.aggregate.vauleobject.enums.status.StepExecutionStatus;
import com.example.core.domain.aggregate.vauleobject.config.ExtractorConfig;
import com.example.core.domain.aggregate.vauleobject.config.StepRouteConfig;
import com.example.core.domain.repository.ApplicationRepository;
import com.example.core.domain.service.registry.BusinessFactExtractorRegistry;
import com.example.core.domain.service.registry.StepActionHandlerRegistry;
import com.example.core.domain.spi.BusinessFactExtractor;
import com.example.core.domain.spi.StepActionHandler;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.ApplicationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

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
public class BusinessOrchestrationAppService {

  private final StepActionHandlerRegistry handlerRegistry;
  private final BusinessFactExtractorRegistry factExtractorRegistry;

  private final BusinessConfigGateway configGateway;
  private final ApplicationRepository applicationRepository;
  private final StepPipelineExecutor pipelineExecutor;
  private final EventBus eventBus;
  private final TransactionTemplate transactionTemplate;

  public void advanceStep(ApplicationId appId) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    BusinessMetaContext context = buildFullConfigContext(app);
    StepRouteConfig routeConfig = configGateway.getNextStep(context, app.currentStep());

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

      app.transit(routeConfig.nextStep());

    } else {
      log.info("流程挂起，物理状态: {}", executionStatus);
    }

    transactionTemplate.executeWithoutResult(status -> {
      applicationRepository.save(app);
      app.getDomainEvents().forEach(eventBus::publish);
      app.clearDomainEvents();
    });
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
