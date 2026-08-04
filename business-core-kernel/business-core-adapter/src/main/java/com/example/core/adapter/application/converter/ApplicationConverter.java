package com.example.core.adapter.application.converter;

import com.example.core.api.application.response.AdvanceStepResponse;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.shared.identifier.id.ApplicationId;
import org.mapstruct.Mapper;

/**
 * 申请单 DTO 转换器
 *
 * <p>通过 MapStruct 完成聚合根到响应 DTO 的转换。
 * 使用 default 方法,因 {@link BusinessApplication} 继承泛型基类,
 * MapStruct @Mapping 无法解析继承的访问器(同 BatchConverter/FormConverter 模式)。
 *
 * <p>后续新增 DTO 转换流程:
 * <ol>
 *   <li>在 API 层定义 Response DTO</li>
 *   <li>在本接口新增转换方法</li>
 *   <li>复杂字段(如嵌套 List)可添加 default 方法辅助</li>
 * </ol>
 *
 * @author panoshu
 */
@Mapper(componentModel = "spring")
public interface ApplicationConverter {

  /**
   * 聚合根 → 摘要响应
   */
  default ApplicationSummaryResponse toSummaryResponse(BusinessApplication app) {
    if (app == null) {
      return null;
    }
    return new ApplicationSummaryResponse(
      app.id().value(),
      app.getBatchId() != null ? app.getBatchId().value() : null,
      app.getStatus() != null ? app.getStatus().name() : null,
      app.currentStep() != null ? app.currentStep().name() : null,
      app.createdAt(),
      app.updatedAt()
    );
  }

  /**
   * 聚合根 → 详情响应
   */
  default ApplicationDetailResponse toDetailResponse(BusinessApplication app) {
    if (app == null) {
      return null;
    }
    return new ApplicationDetailResponse(
      app.id().value(),
      app.getBatchId() != null ? app.getBatchId().value() : null,
      app.getStatus() != null ? app.getStatus().name() : null,
      app.currentStep() != null ? app.currentStep().name() : null,
      app.getParsedJsonFileId() != null ? app.getParsedJsonFileId().value() : null,
      app.getPackageFile() != null && app.getPackageFile().fileId() != null
        ? app.getPackageFile().fileId().value() : null,
      app.getApplyTime(),
      app.getCompleteTime(),
      app.createdAt(),
      app.updatedAt()
    );
  }

  /**
   * 聚合根 → 推进响应
   *
   * <p>{@code nextStep}/{@code status} 需在推进后重新加载聚合根获取,
   * 由 Controller 负责调用 AppService.loadOrThrow 后转换。
   */
  default AdvanceStepResponse toAdvanceStepResponse(BusinessApplication app) {
    if (app == null) {
      return null;
    }
    return new AdvanceStepResponse(
      app.id().value(),
      app.currentStep() != null ? app.currentStep().name() : null,
      app.getStatus() != null ? app.getStatus().name() : null
    );
  }

  /**
   * 申请单 ID → 提交响应
   *
   * <p>当前 {@code needApproval} 固定为 false,{@code approvalInstanceId} 为 null,
   * 审批判断由管道 handler 完成。
   */
  default SubmitResponse toSubmitResponse(ApplicationId applicationId) {
    if (applicationId == null) {
      return null;
    }
    return new SubmitResponse(applicationId.value(), false, null);
  }
}
