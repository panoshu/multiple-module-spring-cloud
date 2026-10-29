package com.example.core.api.application;

import com.example.core.api.application.command.AdvanceStepCommand;
import com.example.core.api.application.command.SubmitApplicationCommand;
import com.example.core.api.application.query.FindApplicationListQuery;
import com.example.core.api.application.query.GetApplicationDetailQuery;
import com.example.core.api.application.response.AdvanceStepResponse;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 业务申请单管理 API（纯契约接口）
 *
 * <p>定义申请单的列表查询、详情查询、推进、提交等公共方法签名，不含传输层注解。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在本接口新增方法签名</li>
 *   <li>在各 xxx-api 的子接口中重新声明并标注 {@code @PostExchange}</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller，通过 ApplicationConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
public interface BusinessApplicationApi {

  /**
   * 查询申请单列表(按批次 ID)。
   */
  ApiResult<List<ApplicationSummaryResponse>> list(@Valid FindApplicationListQuery query);

  /**
   * 查询申请单详情。
   */
  ApiResult<ApplicationDetailResponse> detail(@Valid GetApplicationDetailQuery query);

  /**
   * 推进申请单到下一节点。
   */
  ApiResult<AdvanceStepResponse> advance(@Valid AdvanceStepCommand command);

  /**
   * 提交申请单(触发审批判断)。
   */
  ApiResult<SubmitResponse> submit(@Valid SubmitApplicationCommand command);
}
