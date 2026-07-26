package com.example.core.api.progress;

import com.example.core.api.progress.query.GetBatchProgressQuery;
import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 业务进度查询 API
 *
 * <p>提供批次整体进度的查询能力,聚合批次维度统计与申请单明细。
 * 路径前缀 {@code /core/progress}。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在 API 层新增方法到本接口</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller,通过 ProgressConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@HttpExchange("/core/progress")
public interface BusinessProgressApi {

    /**
     * 查询批次整体进度。
     */
    @PostExchange("/batch")
    ApiResult<BatchProgressResponse> batchProgress(@Valid @RequestBody GetBatchProgressQuery query);
}
