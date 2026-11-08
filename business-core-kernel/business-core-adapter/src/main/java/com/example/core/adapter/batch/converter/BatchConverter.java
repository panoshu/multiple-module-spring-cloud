package com.example.core.adapter.batch.converter;

import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.batch.response.BatchSummaryResponse;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import org.mapstruct.Mapper;

/**
 * 批次 DTO 转换器
 *
 * <p>通过 MapStruct 完成聚合根到响应 DTO 的转换,禁止在 Controller 中直接转换。
 *
 * <p>使用 default 方法实现转换,原因:{@link BusinessBatch} 继承自泛型基类
 * {@code Entity<ID>},{@code id()}/{@code createdAt()}/{@code updatedAt()} 等访问器
 * 由父类提供且不遵循 JavaBean 命名规范,MapStruct 注解处理器无法通过 {@code @Mapping}
 * 自动解析这些继承属性。与 {@code BatchDataConverter} 保持一致的实现模式。
 *
 * <p>后续新增 DTO 转换流程:
 * <ol>
 *   <li>在 API 层定义 Response DTO</li>
 *   <li>在本接口新增转换方法,通过 @Mapping 指定字段映射</li>
 *   <li>复杂字段(如嵌套 List)可添加 default 方法辅助</li>
 * </ol>
 *
 * @author panoshu
 */
@Mapper(componentModel = "spring")
public interface BatchConverter {

  /**
   * 聚合根 → 批次摘要响应
   */
  default BatchSummaryResponse toSummaryResponse(BusinessBatch batch) {
    if (batch == null) {
      return null;
    }
    BusinessContext ctx = batch.businessContext();
    return new BatchSummaryResponse(
      batch.id().value(),
      ctx != null && ctx.businessType() != null ? ctx.businessType().name() : null,
      ctx != null && ctx.planNo() != null ? ctx.planNo().value() : null,
      batch.status() != null ? batch.status().name() : null,
      batch.businessFormRefs() != null ? batch.businessFormRefs().size() : 0,
      batch.totalApplicationCount(),
      batch.successCount(),
      batch.failedCount(),
      batch.createdAt()
    );
  }

  /**
   * 聚合根 → 批次创建响应
   */
  default BatchCreatedResponse toCreatedResponse(BusinessBatch batch) {
    if (batch == null) {
      return null;
    }
    return new BatchCreatedResponse(
      batch.id().value(),
      batch.status() != null ? batch.status().name() : null,
      batch.createdAt()
    );
  }

  /**
   * 聚合根 → 批次详情响应
   *
   * <p>{@code forms} 字段需由调用方另行填充(涉及表单聚合根的级联查询)。
   */
  default BatchDetailResponse toDetailResponse(BusinessBatch batch) {
    if (batch == null) {
      return null;
    }
    BusinessContext ctx = batch.businessContext();
    return new BatchDetailResponse(
      batch.id().value(),
      ctx != null && ctx.businessType() != null ? ctx.businessType().name() : null,
      ctx != null && ctx.planNo() != null ? ctx.planNo().value() : null,
      ctx != null && ctx.customerNo() != null ? ctx.customerNo().value() : null,
      ctx != null ? ctx.customerName() : null,
      batch.status() != null ? batch.status().name() : null,
      batch.businessFormRefs() != null ? batch.businessFormRefs().size() : 0,
      batch.totalApplicationCount(),
      batch.successCount(),
      batch.failedCount(),
      batch.createdAt(),
      batch.updatedAt(),
      null
    );
  }
}
