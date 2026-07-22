package com.example.core.application.service;

import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.core.domain.business.aggregate.valueobject.ParsedPlanResult;
import com.example.core.domain.engine.aggregate.valueobject.config.FormParsingConfig;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.business.repository.FormRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.FormId;
import com.example.shared.primitives.identity.IdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BusinessFormAppService
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 13:01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessFormAppService {

  private final FormRepository formRepository;
  private final ApplicationRepository applicationRepository;
  private final BusinessConfigGateway configGateway;
  private final FileIntegrationGateway fileIntegrationGateway;
  private final EventBus eventBus;
  private final IdService idService;

  /**
   * 步骤 1：确认文件已上传 (由 BFF 层调用)
   */
  @Transactional
  public void confirmUpload(FormId formId, BusinessFile uploadedFile) {
    BusinessForm form = formRepository.loadOrThrow(formId);

    // 聚合根行为：记录上传文件，修改状态为 UPLOADED，内部产生 FormUploadedEvent
    form.markAsUploaded(uploadedFile);

    formRepository.save(form);
    form.getDomainEvents().forEach(eventBus::publish);
    form.clearDomainEvents();
  }

  /**
   * 步骤 2：触发异步解析 (由 FormUploadedEvent 的监听器调用)
   */
  @Transactional
  public void triggerParsing(FormId formId) {
    BusinessForm form = formRepository.loadOrThrow(formId);

    // 查询该表单类型对应的解析配置
    FormParsingConfig parsingConfig = configGateway.getFormParsingConfig(form.buildConfigQueryContext());

    // 防腐调用：让底层基础设施去解析 Excel 拆分出 Json
    fileIntegrationGateway.triggerAsyncParsing(
      form.id(),
      form.getFormFile().fileId(),
      parsingConfig.parseTemplateId(),
      parsingConfig.splitRules()
    );

    // 聚合根行为：修改状态为 PARSING
    form.markAsParsing();
    formRepository.save(form);
  }

  /**
   * 步骤 3：处理底层解析回调并【裂变新生】 (由底层文件服务回调触发)
   */
  @Transactional
  public void handleParsingResult(FormId formId, List<ParsedPlanResult> parsedResults) {
    BusinessForm form = formRepository.loadOrThrow(formId);

    // 聚合根行为：修改状态为 PARSED
    form.markAsParsed();
    formRepository.save(form);

    for (ParsedPlanResult result : parsedResults) {
      // 根据解析出来的计划，创建业务申请聚合根
      BusinessApplication newApp = BusinessApplication.createFromForm(
        idService.nextId(ApplicationId.class, ""),
        form.getBusinessContext(), form.getOperatorInfo(),
        result.jsonFileId() // 底层拆分好的明细 JSON 文件 ID
      );

      applicationRepository.save(newApp);

      newApp.getDomainEvents().forEach(eventBus::publish);
      newApp.clearDomainEvents();
    }

    log.info("表单解析回调处理完成，共创建 {} 个申请单", parsedResults.size());
  }
}
