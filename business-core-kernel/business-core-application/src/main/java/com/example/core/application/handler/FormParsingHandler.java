package com.example.core.application.handler;

import com.example.core.domain.gateway.FileIntegrationGateway;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.root.BusinessForm;
import com.example.core.domain.aggregate.vauleobject.BusinessMetaContext;
import com.example.core.domain.aggregate.vauleobject.enums.status.StepExecutionStatus;
import com.example.core.domain.repository.FormRepository;
import com.example.core.domain.spi.StepActionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DefaultFormParsingHandler
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/14 23:34
 */
@Component
@RequiredArgsConstructor
public class FormParsingHandler implements StepActionHandler {
  private final FileIntegrationGateway fileIntegrationGateway;
  private final FormRepository formRepository;

  @Override
  public String handlerName() {
    return "defaultFormParsingHandler";
  }

  @Override
  public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
    BusinessForm businessForm = formRepository.findByApplicationId(app.id()).orElseThrow();

    // 触发异步解析
    fileIntegrationGateway.triggerAsyncParsing(businessForm, context);

    // 触发异步后，流程挂起，不流转，等待异步回调唤醒
    return StepExecutionStatus.SUCCESS;
  }
}
