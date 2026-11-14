package com.example.core.adapter.application;

import com.example.shared.security.annotation.RequirePermission;
import com.example.core.adapter.application.converter.ApplicationConverter;
import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.api.application.BusinessApplicationApi;
import com.example.core.api.application.command.AdvanceStepCommand;
import com.example.core.api.application.command.SubmitApplicationCommand;
import com.example.core.api.application.query.FindApplicationListQuery;
import com.example.core.api.application.query.GetApplicationDetailQuery;
import com.example.core.api.application.response.AdvanceStepResponse;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.domain.business.gateway.ParsedFileReferenceGateway;
import com.example.shared.security.SessionClaims;
import com.example.core.application.business.service.BusinessApplicationAppService;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.types.ApplicationId;
import com.example.core.types.BatchId;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务申请单管理 Controller
 *
 * <p>实现 {@link BusinessApplicationApi}（纯契约接口）,入口完成会话解析与功能权限校验,
 * 调用 {@link BusinessApplicationAppService} 进行申请单处理。
 *
 * <p>后续新增 Controller 方法流程:
 * <ol>
 *   <li>在 API 层接口新增方法签名</li>
 *   <li>在本类实现方法,标注 @RequirePermission(功能权限码)</li>
 *   <li>通过 ApplicationConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@RestController
@RequestMapping("/core/application")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "core-kernel.controllers.enabled", havingValue = "true", matchIfMissing = false)
public class BusinessApplicationController implements BusinessApplicationApi {

  private final BusinessApplicationAppService applicationAppService;
  private final ApplicationConverter converter;
  private final SessionContextResolver sessionResolver;
  private final ParsedFileReferenceGateway parsedFileGateway;

  @Override
  @PostMapping("/list")
  @RequirePermission(business = "APPLICATION", action = "VIEW")
  public ApiResult<List<ApplicationSummaryResponse>> list(@Valid @RequestBody FindApplicationListQuery query) {
    SessionClaims session = sessionResolver.require();
    log.info("查询申请单列表: batchId={}, status={}, userNo={}",
      query.batchId(), query.status(), session.userNo());

    List<BusinessApplication> apps = applicationAppService.findByBatchId(new BatchId(query.batchId()));
    List<ApplicationSummaryResponse> responses = apps.stream()
      .map(converter::toSummaryResponse)
      .toList();
    return ApiResult.success(responses);
  }

  @Override
  @PostMapping("/detail")
  @RequirePermission(business = "APPLICATION", action = "VIEW")
  public ApiResult<ApplicationDetailResponse> detail(@Valid @RequestBody GetApplicationDetailQuery query) {
    SessionClaims session = sessionResolver.require();
    log.info("查询申请单详情: applicationId={}, userNo={}", query.applicationId(), session.userNo());

    ApplicationId appId = new ApplicationId(query.applicationId());
    BusinessApplication app = applicationAppService.loadOrThrow(appId);
    String jsonFileId = parsedFileGateway.load(appId)
      .map(com.example.file.types.FileId::value).orElse(null);
    return ApiResult.success(converter.toDetailResponse(app, jsonFileId));
  }

  @Override
  @PostMapping("/advance")
  @RequirePermission(business = "APPLICATION", action = "ADVANCE")
  public ApiResult<AdvanceStepResponse> advance(@Valid @RequestBody AdvanceStepCommand command) {
    SessionClaims session = sessionResolver.require();
    log.info("推进申请单: applicationId={}, userNo={}", command.applicationId(), session.userNo());

    ApplicationId appId = new ApplicationId(command.applicationId());
    applicationAppService.advanceApplication(command);
    // 推进后重新加载聚合根以获取最新步骤
    BusinessApplication app = applicationAppService.loadOrThrow(appId);
    return ApiResult.success(converter.toAdvanceStepResponse(app));
  }

  @Override
  @PostMapping("/submit")
  @RequirePermission(business = "APPLICATION", action = "SUBMIT")
  public ApiResult<SubmitResponse> submit(@Valid @RequestBody SubmitApplicationCommand command) {
    SessionClaims session = sessionResolver.require();
    log.info("提交申请单: applicationId={}, userNo={}", command.applicationId(), session.userNo());

    ApplicationId appId = new ApplicationId(command.applicationId());
    applicationAppService.submit(appId);
    return ApiResult.success(converter.toSubmitResponse(appId));
  }
}
