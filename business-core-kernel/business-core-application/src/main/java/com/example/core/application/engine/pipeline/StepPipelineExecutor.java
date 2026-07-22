package com.example.core.application.engine.pipeline;

import com.example.core.application.engine.errorcode.CoreAppErrorCode;
import com.example.core.domain.engine.gateway.ConditionEvaluationGateway;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.engine.aggregate.valueobject.PipelineExecutionResult;
import com.example.core.domain.engine.aggregate.valueobject.config.StepExtensionConfig;
import com.example.core.domain.engine.service.registry.ExtensionActionRegistry;
import com.example.core.domain.engine.spi.StepExtensionAction;
import com.example.shared.exception.SystemException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 步骤管道执行器 (微编排核心)
 * 负责在核心处理器 (Handler) 执行前后，按序、条件驱动地执行扩展动作链 (Pipeline)。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/16 21:13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StepPipelineExecutor {

  private final ExtensionActionRegistry extensionRegistry;
  private final ConditionEvaluationGateway conditionEvaluator;

  /**
   * 执行扩展动作链
   *
   * @param app        业务聚合根 (活体对象)
   * @param context    全局只读上下文
   * @param extensions 配置中心下发的扩展点配置列表
   * @return 执行结果 (包含可能的上下文突变数据 mutations)
   */
  public PipelineExecutionResult executeChain(BusinessApplication app, BusinessMetaContext context, List<StepExtensionConfig> extensions) {
    if (extensions == null || extensions.isEmpty()) {
      return new PipelineExecutionResult(true, List.of());
    }

    List<PipelineExecutionResult.ActionExecutionRecord> records = new ArrayList<>();
    List<StepExtensionConfig> sortedExtensions = sortExtensions(extensions);

    for (StepExtensionConfig config : sortedExtensions) {
      String actionName = config.beanName(); // 使用 beanName 作为标识最安全

      // 1. 宏观条件过滤：不满足则直接记录为 SKIPPED
      if (!isConditionMatched(config, context)) {
        records.add(PipelineExecutionResult.ActionExecutionRecord.skipped(actionName));
        continue;
      }

      StepExtensionAction action = extensionRegistry.get(actionName);

      // 2. 策略路由分发：调用语义化方法返回 Record
      PipelineExecutionResult.ActionExecutionRecord record;
      if (config.isAsync()) {
        record = dispatchAsyncAction(action, context, config);
      } else {
        record = executeSyncAction(action, app, context, config);
      }

      // 3. 收集确定的飞行日志
      records.add(record);
    }

    // 4. 聚合执行结果:致命失败已在 executeSyncAction/handleUnexpectedException 中抛出 SystemException 中断,
    //    到达此处表示链路完整跑完。isSuccess 仅在所有动作均非 FAILURE 时为 true,
    //    使调用方能够区分"链路完成但有非致命失败"与"全部成功"。
    boolean isSuccess = records.stream()
      .noneMatch(record -> PipelineExecutionResult.ActionStatus.FAILURE.equals(record.status()));
    return new PipelineExecutionResult(isSuccess, records);
  }


  private List<StepExtensionConfig> sortExtensions(List<StepExtensionConfig> extensions) {
    return extensions.stream()
      .sorted(Comparator.comparingInt(StepExtensionConfig::order))
      .toList();
  }

  private boolean isConditionMatched(StepExtensionConfig config, BusinessMetaContext context) {
    boolean matched = conditionEvaluator.evaluate(config.condition(), context);
    if (!matched) {
      log.debug("扩展动作 [{}] 未满足条件表达式 [{}], 跳过执行", config.beanName(), config.condition());
    }
    return matched;
  }

  private PipelineExecutionResult.ActionExecutionRecord dispatchAsyncAction(StepExtensionAction action, BusinessMetaContext context, StepExtensionConfig config) {
    long startTime = System.currentTimeMillis();

    CompletableFuture.runAsync(() -> {
      try {
        action.execute(null, context, config.params());
        log.info("异步动作 [{}] 执行完毕", action.actionName());
      } catch (Exception e) {
        log.error("异步扩展动作 [{}] 执行异常", config.beanName(), e);
      }
    });

    long costTime = System.currentTimeMillis() - startTime;
    // 语义化返回：异步派发记录
    return PipelineExecutionResult.ActionExecutionRecord.async(action.actionName(), costTime);
  }

  private PipelineExecutionResult.ActionExecutionRecord executeSyncAction(StepExtensionAction action, BusinessApplication app, BusinessMetaContext context, StepExtensionConfig config) {
    long startTime = System.currentTimeMillis();
    ExtensionExecutionResult result;

    try {
      result = action.execute(app, context, config.params());
    } catch (Exception e) {
      return handleUnexpectedException(e, action, config, startTime);
    }

    long costTime = System.currentTimeMillis() - startTime;

    // 处理数据突变
    applyContextMutations(context, result);
    // 处理业务阻断 (如果是关键节点且失败，内部会直接抛出 SystemException 中断流程)
    handleBusinessFailure(result, config);

    // 语义化返回：成功或失败的业务记录
    if (!result.isSuccess()) {
      return PipelineExecutionResult.ActionExecutionRecord.failure(action.actionName(), costTime, result);
    }
    return PipelineExecutionResult.ActionExecutionRecord.success(action.actionName(), costTime, result);
  }

  private void applyContextMutations(BusinessMetaContext context, ExtensionExecutionResult result) {
    if (result.mutations() != null && !result.mutations().isEmpty()) {
      context.extensionFacts().putAll(result.mutations());
    }
  }

  private PipelineExecutionResult.ActionExecutionRecord handleUnexpectedException(Exception e, StepExtensionAction action, StepExtensionConfig config, long startTime) {
    long costTime = System.currentTimeMillis() - startTime;
    ExtensionExecutionResult failureResult = ExtensionExecutionResult.failure("500", e.getMessage());

    if (config.isCritical()) {
      log.error("致命动作 [{}] 抛出未捕获异常，中断流程", config.beanName(), e);
      throw new SystemException(CoreAppErrorCode.STEP_HANDLER_FAILED, e)
        .withLogDetail("致命动作执行异常");
    } else {
      log.error("非致命动作 [{}] 抛出未捕获异常，忽略并继续", config.beanName(), e);
      // 语义化返回：异常失败记录
      return PipelineExecutionResult.ActionExecutionRecord.failure(action.actionName(), costTime, failureResult);
    }
  }

  private void handleBusinessFailure(ExtensionExecutionResult result, StepExtensionConfig config) {
    if (result.isSuccess()) {
      return;
    }
    if (config.isCritical()) {
      log.error("致命动作 [{}] 业务执行失败，中断流程: {}", config.beanName(), result.errorMessage());
      throw new SystemException(CoreAppErrorCode.STEP_HANDLER_FAILED)
        .withLogDetail("code: %s, message: %s".formatted(result.errorCode(), result.errorMessage()));
    } else {
      log.warn("非致命动作 [{}] 业务执行失败，忽略并继续: {}", config.beanName(), result.errorMessage());
    }
  }
}
