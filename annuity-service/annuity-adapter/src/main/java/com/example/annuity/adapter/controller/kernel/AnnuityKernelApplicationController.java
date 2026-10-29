package com.example.annuity.adapter.controller.kernel;

import com.example.auth.api.annotation.RequirePermission;
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
import com.example.core.api.context.SessionContext;
import com.example.core.application.business.service.BusinessApplicationAppService;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.BatchId;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 年金服务 - 申请单管理 Controller（服务级路径前缀版）
 *
 * <p>实现 {@link BusinessApplicationApi}（纯契约接口），路径前缀为 {@code /annuity/core/application}。
 *
 * @author panoshu
 */
@Slf4j
@RestController
@RequestMapping("/annuity/core/application")
@RequiredArgsConstructor
public class AnnuityKernelApplicationController implements BusinessApplicationApi {

  private final BusinessApplicationAppService applicationAppService;
  private final ApplicationConverter converter;
  private final SessionContextResolver sessionResolver;

  @Override
  @PostMapping("/list")
  @RequirePermission(business = "APPLICATION", action = "VIEW")
  public ApiResult<List<ApplicationSummaryResponse>> list(@Valid @RequestBody FindApplicationListQuery query) {
    SessionContext session = sessionResolver.require();
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
    SessionContext session = sessionResolver.require();
    log.info("查询申请单详情: applicationId={}, userNo={}", query.applicationId(), session.userNo());

    BusinessApplication app = applicationAppService.loadOrThrow(new ApplicationId(query.applicationId()));
    return ApiResult.success(converter.toDetailResponse(app));
  }

  @Override
  @PostMapping("/advance")
  @RequirePermission(business = "APPLICATION", action = "ADVANCE")
  public ApiResult<AdvanceStepResponse> advance(@Valid @RequestBody AdvanceStepCommand command) {
    SessionContext session = sessionResolver.require();
    log.info("推进申请单: applicationId={}, userNo={}", command.applicationId(), session.userNo());

    ApplicationId appId = new ApplicationId(command.applicationId());
    applicationAppService.advanceStep(appId);
    BusinessApplication app = applicationAppService.loadOrThrow(appId);
    return ApiResult.success(converter.toAdvanceStepResponse(app));
  }

  @Override
  @PostMapping("/submit")
  @RequirePermission(business = "APPLICATION", action = "SUBMIT")
  public ApiResult<SubmitResponse> submit(@Valid @RequestBody SubmitApplicationCommand command) {
    SessionContext session = sessionResolver.require();
    log.info("提交申请单: applicationId={}, userNo={}", command.applicationId(), session.userNo());

    ApplicationId appId = new ApplicationId(command.applicationId());
    applicationAppService.submit(appId);
    return ApiResult.success(converter.toSubmitResponse(appId));
  }
}
