package com.example.annuity.adapter.controller.kernel;

import com.example.auth.api.annotation.RequirePermission;
import com.example.core.adapter.batch.converter.BatchConverter;
import com.example.core.adapter.context.BusinessMetaContextAssembler;
import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.adapter.validator.SupportedBusinessTypeValidator;
import com.example.core.api.batch.BusinessBatchApi;
import com.example.core.api.batch.command.CancelBatchCommand;
import com.example.core.api.batch.command.CreateBatchCommand;
import com.example.core.api.batch.query.FindActiveBatchQuery;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.batch.response.BatchSummaryResponse;
import com.example.core.api.context.BusinessMetaContext;
import com.example.core.api.context.SessionContext;
import com.example.core.application.business.guard.BusinessAccessGuard;
import com.example.core.application.business.service.BusinessBatchAppService;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.types.BatchId;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 年金服务 - 批次管理 Controller（服务级路径前缀版）
 *
 * <p>实现 {@link BusinessBatchApi}（纯契约接口），路径前缀为 {@code /annuity/core/batch}，
 * 使 BFF 侧可通过 httpexchange 按 service-specific 接口路由到本服务。
 *
 * <p>入口完成五步:
 * <ol>
 *   <li>业务类型校验({@link SupportedBusinessTypeValidator})</li>
 *   <li>会话解析({@link SessionContextResolver})</li>
 *   <li>BusinessMetaContext 组装({@link BusinessMetaContextAssembler})</li>
 *   <li>权限校验({@link BusinessAccessGuard})</li>
 *   <li>调用 AppService({@link BusinessBatchAppService})</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@RestController
@RequestMapping("/annuity/core/batch")
@RequiredArgsConstructor
public class AnnuityKernelBatchController implements BusinessBatchApi {

  private final BusinessBatchAppService batchAppService;
  private final BatchConverter converter;
  private final SupportedBusinessTypeValidator typeValidator;
  private final SessionContextResolver sessionResolver;
  private final BusinessMetaContextAssembler metaAssembler;
  private final BusinessAccessGuard accessGuard;

  @Override
  @PostMapping("/active")
  @RequirePermission(business = "BATCH", action = "VIEW")
  public ApiResult<BatchSummaryResponse> findActive(@Valid @RequestBody FindActiveBatchQuery query) {
    typeValidator.validate(query.businessType());
    SessionContext session = sessionResolver.require();
    log.info("查询未完成批次: planNo={}, businessType={}, userNo={}",
      query.planNo(), query.businessType(), session.userNo());

    Optional<BusinessBatch> batch = batchAppService.findActive(
      new PlanNo(query.planNo()),
      BusinessType.valueOf(query.businessType())
    );
    return ApiResult.success(batch.map(converter::toSummaryResponse).orElse(null));
  }

  @Override
  @PostMapping("/create")
  @RequirePermission(business = "BATCH", action = "CREATE")
  public ApiResult<BatchCreatedResponse> create(@Valid @RequestBody CreateBatchCommand command) {
    typeValidator.validate(command.businessType());
    SessionContext session = sessionResolver.require();
    BusinessMetaContext meta = metaAssembler.assemble(command.businessType(), command.planNo(), session);
    accessGuard.checkCanHandle(session, meta);

    log.info("创建业务批次: businessType={}, planNo={}, userNo={}",
      command.businessType(), command.planNo(), session.userNo());

    BusinessContext domainContext = toDomainContext(meta, session);
    OperatorInfo operator = toOperatorInfo(session);
    BusinessBatch batch = batchAppService.createBatch(domainContext, operator);
    return ApiResult.success(converter.toCreatedResponse(batch));
  }

  @Override
  @PostMapping("/detail")
  @RequirePermission(business = "BATCH", action = "VIEW")
  public ApiResult<BatchDetailResponse> detail(@Valid @RequestBody GetBatchDetailQuery query) {
    SessionContext session = sessionResolver.require();
    log.info("查询批次详情: batchId={}, userNo={}", query.batchId(), session.userNo());
    BusinessBatch batch = batchAppService.loadOrThrow(new BatchId(query.batchId()));
    return ApiResult.success(converter.toDetailResponse(batch));
  }

  @Override
  @PostMapping("/cancel")
  @RequirePermission(business = "BATCH", action = "CANCEL")
  public ApiResult<Void> cancel(@Valid @RequestBody CancelBatchCommand command) {
    SessionContext session = sessionResolver.require();
    log.info("取消批次: batchId={}, userNo={}", command.batchId(), session.userNo());
    batchAppService.cancel(new BatchId(command.batchId()), command.reason());
    return ApiResult.success();
  }

  private BusinessContext toDomainContext(BusinessMetaContext meta, SessionContext session) {
    return new BusinessContext(
      BusinessType.valueOf(meta.businessType()),
      new CustomerNo(meta.customerNo()),
      meta.customerName(),
      new ProductNo(meta.productNo()),
      meta.productName(),
      new PlanNo(meta.planNo()),
      meta.planName(),
      OperationModel.valueOf(meta.operationModel()),
      AccountManager.valueOf(meta.accountManager())
    );
  }

  private OperatorInfo toOperatorInfo(SessionContext session) {
    AnnuityChannel channel = mapChannel(session.channelType());
    return new OperatorInfo(
      channel,
      new UserNo(session.userNo()),
      session.loginName(),
      session.isProxy()
    );
  }

  private AnnuityChannel mapChannel(String channelType) {
    if (channelType == null) {
      return AnnuityChannel.NETAPP;
    }
    return switch (channelType) {
      case "INTERNET" -> AnnuityChannel.NETAPP;
      case "BRANCH" -> AnnuityChannel.CJ_TELLER;
      case "HQ" -> AnnuityChannel.REGIONAL_CENTER;
      default -> AnnuityChannel.NETAPP;
    };
  }
}
