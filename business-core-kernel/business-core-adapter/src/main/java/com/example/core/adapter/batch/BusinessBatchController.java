package com.example.core.adapter.batch;

import com.example.shared.security.annotation.RequirePermission;
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
import com.example.shared.security.SessionClaims;
import com.example.core.application.business.guard.BusinessAccessGuard;
import com.example.core.application.business.service.BusinessBatchAppService;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.types.Channel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.types.OperatingMode;
import com.example.shared.enumeration.EnumCodeCache;
import com.example.core.types.BatchId;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.core.types.CustomerNo;
import com.example.core.types.UserNo;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 业务批次管理 Controller
 *
 * <p>实现 {@link BusinessBatchApi}（纯契约接口）,入口完成五步:
 * <ol>
 *   <li>业务类型校验({@link SupportedBusinessTypeValidator})</li>
 *   <li>会话解析({@link SessionContextResolver})</li>
 *   <li>BusinessMetaContext 组装({@link BusinessMetaContextAssembler})</li>
 *   <li>权限校验({@link BusinessAccessGuard})</li>
 *   <li>调用 AppService({@link BusinessBatchAppService})</li>
 * </ol>
 *
 * <p>方法签名与 API 接口完全一致,会话通过 {@link SessionContextResolver} 内部
 * 使用 {@code RequestContextHolder} 获取当前请求解析。
 *
 * <p>后续新增 Controller 方法流程:
 * <ol>
 *   <li>在 API 层接口新增方法签名</li>
 *   <li>在本类实现方法,标注 @RequirePermission(功能权限码)</li>
 *   <li>入口完成上述五步,通过 MapStruct Converter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@RestController
@RequestMapping("/core/batch")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "core-kernel.controllers.enabled", havingValue = "true", matchIfMissing = false)
public class BusinessBatchController implements BusinessBatchApi {

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
    SessionClaims session = sessionResolver.require();
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
    SessionClaims session = sessionResolver.require();
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
    SessionClaims session = sessionResolver.require();
    log.info("查询批次详情: batchId={}, userNo={}", query.batchId(), session.userNo());
    BusinessBatch batch = batchAppService.loadOrThrow(new BatchId(query.batchId()));
    return ApiResult.success(converter.toDetailResponse(batch));
  }

  @Override
  @PostMapping("/cancel")
  @RequirePermission(business = "BATCH", action = "CANCEL")
  public ApiResult<Void> cancel(@Valid @RequestBody CancelBatchCommand command) {
    SessionClaims session = sessionResolver.require();
    log.info("取消批次: batchId={}, userNo={}", command.batchId(), session.userNo());
    batchAppService.cancel(new BatchId(command.batchId()), command.reason());
    return ApiResult.success();
  }

  /**
   * 将 BusinessMetaContext + SessionClaims 转换为领域 BusinessContext。
   */
  private BusinessContext toDomainContext(BusinessMetaContext meta, SessionClaims session) {
    return new BusinessContext(
      BusinessType.valueOf(meta.businessType()),
      new CustomerNo(meta.customerNo()),
      meta.customerName(),
      new ProductNo(meta.productNo()),
      meta.productName(),
      new PlanNo(meta.planNo()),
      meta.planName(),
      EnumCodeCache.get(OperatingMode.class, meta.operationModel()),
      AccountManager.valueOf(meta.accountManager())
    );
  }

  /**
   * 从 SessionClaims 组装 OperatorInfo。
   *
   * <p>渠道映射:SessionClaims.channelType(String) → Channel 枚举。
   * isProxy 直接取自 SessionClaims(仅 INTERNET 渠道为 true)。
   */
  private OperatorInfo toOperatorInfo(SessionClaims session) {
    Channel channel = mapChannel(session.channelType());
    return new OperatorInfo(
      channel,
      session.userNo(),
      session.loginName(),
      session.isProxy()
    );
  }

  /**
   * 渠道字符串映射为 Channel 枚举。
   *
   * <p>映射规则:
   * <ul>
   *   <li>INTERNET → NETAPP(网上渠道,含代办)</li>
   *   <li>BRANCH → TELLER(网点渠道,需二次授权)</li>
   *   <li>HQ → REGIONAL_CENTER(总部渠道)</li>
   * </ul>
   */
  private Channel mapChannel(String channelType) {
    if (channelType == null) {
      return Channel.NETAPP;
    }
    return switch (channelType) {
      case "INTERNET" -> Channel.NETAPP;
      case "BRANCH" -> Channel.TELLER;
      case "HQ" -> Channel.REGIONAL_CENTER;
      default -> Channel.NETAPP;
    };
  }
}
