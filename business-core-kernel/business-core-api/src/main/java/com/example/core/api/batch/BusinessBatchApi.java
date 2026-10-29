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

/**
 * 业务批次管理 API（纯契约接口）
 *
 * <p>定义批次的查询未完成、创建、详情、取消等公共方法签名，不含传输层注解。
 * 各业务服务的 {@code xxx-api} 模块通过继承本接口定义服务专有的
 * {@code @HttpExchange} 接口供客户端代理调用；各业务服务的 adapter controller
 * 实现本接口保证方法签名编译期一致。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在本接口新增方法签名</li>
 *   <li>在各 xxx-api 的子接口中重新声明并标注 {@code @PostExchange}</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller，入口完成业务类型校验→会话解析→权限校验→调用 AppService</li>
 *   <li>通过 MapStruct Converter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
public interface BusinessBatchApi {

  /**
   * 查询指定计划+业务类型的未完成/处理中批次。
   *
   * <p>无未完成批次时返回 {@code ApiResult.success(null)}。
   */
  ApiResult<BatchSummaryResponse> findActive(@Valid FindActiveBatchQuery query);

  /**
   * 创建新批次。
   *
   * <p>前端只传 businessType + planNo,后端从 SessionContext 组装完整元数据。
   */
  ApiResult<BatchCreatedResponse> create(@Valid CreateBatchCommand command);

  /**
   * 查询批次详情(含表单/申请单摘要)。
   */
  ApiResult<BatchDetailResponse> detail(@Valid GetBatchDetailQuery query);

  /**
   * 取消未提交批次。
   */
  ApiResult<Void> cancel(@Valid CancelBatchCommand command);
}
