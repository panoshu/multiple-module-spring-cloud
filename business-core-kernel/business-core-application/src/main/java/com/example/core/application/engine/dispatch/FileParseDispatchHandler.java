package com.example.core.application.engine.dispatch;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.config.FormParsingConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * FILE_PARSE 类型的事务外派发处理器（spec §9.2 阶段 B，批次 29）。
 * <p>
 * 解析参数（formId / sourceFileId / parseTemplateId / splitRules）均可在事务外从
 * {@link BusinessApplication} 与 {@link BusinessConfigGateway} 重建，无需持久化派发载荷；
 * requestNo 取自协作记录（{@code SE-<stepExecutionId>}，与批次 16/21 同源）。
 * 配置缺失时返回 null，协作保持 DISPATCHING 交由恢复任务对账，绝不盲目派发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileParseDispatchHandler implements InteractionDispatchHandler {

  private final ApplicationRepository applicationRepository;
  private final BusinessConfigGateway configGateway;
  private final FileIntegrationGateway fileIntegrationGateway;

  @Override
  public InteractionType supportedType() {
    return InteractionType.FILE_PARSE;
  }

  @Override
  public String dispatch(ExternalInteraction interaction) {
    BusinessApplication app = applicationRepository.loadOrThrow(interaction.applicationId());
    FormParsingConfig config = configGateway.getFormParsingConfig(app.buildConfigQueryContext());
    if (config == null || !StringUtils.hasText(config.parseTemplateId())) {
      log.warn("表单解析配置缺失，FILE_PARSE 协作保持 DISPATCHING 待对账: interactionId={}, requestNo={}",
        interaction.id(), interaction.requestNo());
      return null;
    }
    return fileIntegrationGateway.triggerAsyncParsingAndGetTaskId(
      app.formId(),
      app.requireFileForParsing(),
      config.parseTemplateId(),
      config.splitRules(),
      interaction.requestNo()
    );
  }
}
