package com.example.annuity.adapter.converter;

import com.example.annuity.api.dto.ApplicationResponse;
import com.example.annuity.api.dto.BatchStatusResponse;
import com.example.annuity.api.dto.UploadFormRequest;
import com.example.annuity.application.command.UploadFormCommand;
import com.example.annuity.application.result.ApplicationQueryResult;
import com.example.annuity.application.result.BatchQueryResult;
import com.example.annuity.application.result.UploadFormResult;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.ProductNo;
import com.example.shared.identifier.id.UserNo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 年金服务 API 层 Converter
 * <p>
 * 负责以下转换：
 * <ul>
 *   <li>{@link UploadFormRequest} → {@link UploadFormCommand}：通过 default 方法构建
 *       {@link BusinessContext} 和 {@link OperatorInfo} 领域原语</li>
 *   <li>{@link UploadFormResult} → {@link BatchStatusResponse}：上传响应转换</li>
 *   <li>{@link ApplicationQueryResult} → {@link ApplicationResponse}：申请单详情转换</li>
 *   <li>{@link BatchQueryResult} → {@link BatchStatusResponse}：批次状态转换</li>
 * </ul>
 * 严格遵守项目规则：所有 DTO 转换通过 MapStruct Converter，禁止在 Adapter 中直接转换。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Mapper(componentModel = "spring")
public interface AnnuityApiConverter {

  DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  /**
   * 上传请求 → 应用层 Command
   * <p>
   * 复杂转换（需构建 BusinessContext/OperatorInfo 领域原语），使用 default 方法手动构造。
   */
  default UploadFormCommand toCommand(UploadFormRequest request) {
    if (request == null) {
      return null;
    }
    return new UploadFormCommand(
      toBusinessContext(request),
      toOperatorInfo(request),
      request.fileName(),
      request.fileSize(),
      request.planType(),
      request.initialContribution(),
      request.hasForeignInvestment()
    );
  }

  /**
   * 构建业务上下文领域原语
   */
  default BusinessContext toBusinessContext(UploadFormRequest request) {
    return new BusinessContext(
      BusinessType.valueOf(request.businessType()),
      CustomerNo.of(request.customerNo()),
      null,
      ProductNo.of(request.productNo()),
      null,
      PlanNo.of(request.planNo()),
      null,
      request.operationModel() != null ? OperationModel.valueOf(request.operationModel()) : null,
      request.accountManager() != null ? AccountManager.valueOf(request.accountManager()) : null
    );
  }

  /**
   * 构建操作人信息领域原语
   */
  default OperatorInfo toOperatorInfo(UploadFormRequest request) {
    return new OperatorInfo(
      request.channel() != null ? AnnuityChannel.valueOf(request.channel()) : null,
      UserNo.of(request.operatorId()),
      request.operatorName(),
      false
    );
  }

  /**
   * 上传结果 → 批次状态响应
   */
  @Mapping(target = "applications", ignore = true)
  BatchStatusResponse toBatchResponse(UploadFormResult result);

  /**
   * 申请单查询结果 → 申请单响应
   */
  @Mapping(target = "createdTime", source = "createdTime", qualifiedByName = "formatDateTime")
  @Mapping(target = "updatedTime", source = "updatedTime", qualifiedByName = "formatDateTime")
  ApplicationResponse toApplicationResponse(ApplicationQueryResult result);

  /**
   * 批次查询结果 → 批次状态响应
   */
  @Mapping(target = "applications", source = "applications")
  BatchStatusResponse toBatchStatusResponse(BatchQueryResult result);

  /**
   * 批次内申请单摘要转换
   */
  BatchStatusResponse.ApplicationSummary toApplicationSummary(BatchQueryResult.ApplicationSummary summary);

  /**
   * LocalDateTime → ISO 字符串
   */
  @Named("formatDateTime")
  default String formatDateTime(LocalDateTime dateTime) {
    return dateTime != null ? DATE_TIME_FORMATTER.format(dateTime) : null;
  }

  /**
   * 默认空列表（用于 UploadFormResult 转 BatchStatusResponse 时填充 applications）
   */
  default List<BatchStatusResponse.ApplicationSummary> emptyApplicationList() {
    return List.of();
  }
}
