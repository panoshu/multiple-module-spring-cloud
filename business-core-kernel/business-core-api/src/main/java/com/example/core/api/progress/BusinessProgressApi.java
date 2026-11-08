package com.example.core.api.progress;

import com.example.core.api.progress.query.GetBatchProgressQuery;
import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;

/**
 * 业务进度查询 API（纯契约接口）
 *
 * <p>定义批次整体进度的查询方法签名，不含传输层注解。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在本接口新增方法签名</li>
 *   <li>在各 xxx-api 的子接口中重新声明并标注 {@code @PostExchange}</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller，通过 ProgressConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
public interface BusinessProgressApi {

  /**
   * 查询批次整体进度。
   */
  ApiResult<BatchProgressResponse> batchProgress(@Valid GetBatchProgressQuery query);
}
