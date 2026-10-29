package com.example.bff.shared.business;

import com.example.bff.shared.business.dto.BatchOverviewResponse;
import com.example.bff.shared.business.dto.BffApplicationDetailRequest;
import com.example.bff.shared.business.dto.BffBatchDetailRequest;
import com.example.bff.shared.business.dto.BffBatchOverviewRequest;
import com.example.bff.shared.business.dto.BffCreateBatchRequest;
import com.example.bff.shared.business.dto.BffFormTokenRequest;
import com.example.bff.shared.business.dto.BffListMaterialsRequest;
import com.example.bff.shared.business.dto.BffSubmitRequest;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.shared.web.core.api.ApiResult;

import java.util.List;
import java.util.Set;

/**
 * 业务场景处理器 — 每组相关 businessType 一个实现
 *
 * <p>BFF 收到请求后，根据 {@code businessType} 查找对应 handler 并委托执行。
 * 新增业务场景只需新增实现类，不改动已有代码。
 *
 * <p>简单转发方法（createBatch / batchDetail 等）由 handler 内部调用对应服务的
 * kernel API 代理完成；聚合方法（batchOverview）由各 handler 按需覆写，
 * 编排多个服务端 API 并组装聚合响应。
 *
 * @author bff
 */
public interface BusinessScenarioHandler {

  /**
   * 本处理器支持的业务类型标识集合。
   *
   * <p>值与前端请求中的 {@code businessType} 字段一致，
   * 例如 {@code Set.of("ANNUITY_OPEN", "ANNUITY_CHANGE")}。
   * 一个 handler 可覆盖同一业务线的多个子类型。
   */
  Set<String> supportedTypes();

  /**
   * 创建批次 — 简单转发到业务服务的 kernel batch API。
   */
  ApiResult<BatchCreatedResponse> createBatch(BffCreateBatchRequest request);

  /**
   * 查询批次详情 — 简单转发到业务服务的 kernel batch API。
   */
  ApiResult<BatchDetailResponse> batchDetail(BffBatchDetailRequest request);

  /**
   * 申请上传 token — 简单转发到业务服务的 kernel form API。
   */
  ApiResult<UploadTokenResponse> applyUploadToken(BffFormTokenRequest request);

  /**
   * 提交申请单 — 简单转发到业务服务的 kernel application API。
   */
  ApiResult<SubmitResponse> submitApplication(BffSubmitRequest request);

  /**
   * 查询申请单详情 — 简单转发到业务服务的 kernel application API。
   */
  ApiResult<ApplicationDetailResponse> applicationDetail(BffApplicationDetailRequest request);

  /**
   * 查询材料列表 — 简单转发到业务服务的 kernel material API。
   */
  ApiResult<List<MaterialItemResponse>> listMaterials(BffListMaterialsRequest request);

  /**
   * 批次概览（聚合场景）— 各业务按需覆写。
   *
   * <p>默认实现回退到 {@link #batchDetail(BffBatchDetailRequest)}，
   * 不含进度和申请单聚合数据。需要聚合的 handler 应覆写此方法，
   * 并发调用 batch / progress / application 等服务端 API。
   */
  default ApiResult<BatchOverviewResponse> batchOverview(BffBatchOverviewRequest request) {
    BatchDetailResponse detail = batchDetail(
      new BffBatchDetailRequest(request.businessType(), request.batchId())
    ).data();
    return ApiResult.success(BffResponseAssembler.assemble(detail, null, null));
  }
}
