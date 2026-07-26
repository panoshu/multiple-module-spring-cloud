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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 业务申请单管理 API
 *
 * <p>提供申请单的列表查询、详情查询、推进、提交等公共接口。
 * 路径前缀 {@code /core/application}。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在 API 层新增方法到本接口</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller,通过 ApplicationConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@HttpExchange("/core/application")
public interface BusinessApplicationApi {

    /**
     * 查询申请单列表(按批次 ID)。
     */
    @PostExchange("/list")
    ApiResult<List<ApplicationSummaryResponse>> list(@Valid @RequestBody FindApplicationListQuery query);

    /**
     * 查询申请单详情。
     */
    @PostExchange("/detail")
    ApiResult<ApplicationDetailResponse> detail(@Valid @RequestBody GetApplicationDetailQuery query);

    /**
     * 推进申请单到下一节点。
     */
    @PostExchange("/advance")
    ApiResult<AdvanceStepResponse> advance(@Valid @RequestBody AdvanceStepCommand command);

    /**
     * 提交申请单(触发审批判断)。
     */
    @PostExchange("/submit")
    ApiResult<SubmitResponse> submit(@Valid @RequestBody SubmitApplicationCommand command);
}
