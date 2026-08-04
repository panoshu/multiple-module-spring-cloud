package com.example.core.api.batch;

import com.example.core.api.batch.command.CancelBatchCommand;
import com.example.core.api.batch.command.CreateBatchCommand;
import com.example.core.api.batch.query.FindActiveBatchQuery;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.batch.response.BatchSummaryResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Optional;

/**
 * 业务批次管理 API
 *
 * <p>提供批次的查询未完成、创建、详情、取消等公共接口,所有业务类型共用。
 * 路径前缀 {@code /core/batch}。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在 API 层新增方法到本接口(或新建 Api 接口),路径前缀 /core</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller,入口完成业务类型校验→会话解析→权限校验→调用 AppService</li>
 *   <li>通过 MapStruct Converter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@HttpExchange("/core/batch")
public interface BusinessBatchApi {

  /**
   * 查询指定计划+业务类型的未完成/处理中批次。
   */
  @PostExchange("/active")
  ApiResult<Optional<BatchSummaryResponse>> findActive(@Valid @RequestBody FindActiveBatchQuery query);

  /**
   * 创建新批次。
   *
   * <p>前端只传 businessType + planNo,后端从 SessionContext 组装完整元数据。
   */
  @PostExchange("/create")
  ApiResult<BatchCreatedResponse> create(@Valid @RequestBody CreateBatchCommand command);

  /**
   * 查询批次详情(含表单/申请单摘要)。
   */
  @PostExchange("/detail")
  ApiResult<BatchDetailResponse> detail(@Valid @RequestBody GetBatchDetailQuery query);

  /**
   * 取消未提交批次。
   */
  @PostExchange("/cancel")
  ApiResult<Void> cancel(@Valid @RequestBody CancelBatchCommand command);
}
