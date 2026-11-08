package com.example.core.adapter.progress.converter;

import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import org.mapstruct.Mapper;

/**
 * 进度 DTO 转换器
 *
 * <p>通过 MapStruct 完成聚合根到响应 DTO 的转换。
 * 使用 default 方法,因 {@link BusinessBatch} 继承泛型基类,
 * MapStruct @Mapping 无法解析继承的访问器。
 *
 * @author panoshu
 */
@Mapper(componentModel = "spring")
public interface ProgressConverter {

  /**
   * 批次聚合根 → 进度响应
   */
  default BatchProgressResponse toBatchProgressResponse(BusinessBatch batch) {
    if (batch == null) {
      return null;
    }
    int total = batch.totalApplicationCount();
    int success = batch.successCount();
    int failed = batch.failedCount();
    int pending = Math.max(0, total - success - failed);
    return new BatchProgressResponse(
      batch.id().value(),
      batch.status() != null ? batch.status().name() : null,
      total,
      success,
      failed,
      pending
    );
  }
}
