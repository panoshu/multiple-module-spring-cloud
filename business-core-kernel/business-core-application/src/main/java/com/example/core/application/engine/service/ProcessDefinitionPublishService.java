package com.example.core.application.engine.service;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.gateway.ProcessDefinitionStore;
import com.example.core.domain.engine.service.registry.StepActionHandlerRegistry;
import com.example.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 流程定义发布服务（spec §7.1 发布约束）。
 * <p>
 * 统一发布入口：校验主处理器已注册、拒绝重复版本（已发布定义不可变），
 * 发布后由存储更新 latest 指针（缓存失效）。定义自身不变量（步骤编码唯一、
 * 初始步骤存在、后继步骤存在、外部步骤配置完整）由 ProcessDefinition 与
 * StepDefinition 的紧凑构造器保证；条件表达式语法预校验待专项批次落地。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefinitionPublishService {

  private final ProcessDefinitionStore store;
  private final StepActionHandlerRegistry handlerRegistry;

  public void publish(ProcessDefinition definition) {
    if (definition == null) {
      throw new DomainException(CoreDomainErrorCode.PROCESS_DEFINITION_INVALID)
        .withLogDetail("待发布流程定义不能为空");
    }
    validateHandlers(definition);
    store.publish(definition);
    log.info("流程定义发布完成: businessType={}, version={}",
      definition.businessType().value(), definition.version().value());
  }

  private void validateHandlers(ProcessDefinition definition) {
    for (StepDefinition step : definition.steps()) {
      String processor = step.mainProcessor();
      if (StringUtils.hasText(processor) && !handlerRegistry.contains(processor)) {
        throw new DomainException(CoreDomainErrorCode.PROCESS_DEFINITION_INVALID)
          .withLogDetail("流程定义引用了未注册的主处理器: " + processor);
      }
    }
  }
}
