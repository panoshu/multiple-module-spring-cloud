package com.example.annuity.application.extension;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.dto.AnnuityEmployeeDTO;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.domain.service.AnnuityEmployeeMapper;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.core.application.engine.step.extension.AbstractJsonStreamIngestionAction;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;

/**
 * 年金员工明细 JSON 流式摄入扩展动作
 * <p>
 * 继承 kernel 的 {@link AbstractJsonStreamIngestionAction},实现 3 个钩子:
 * mapToEntity / saveBatch / extractTraceId。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Component("annuityDetailIngestionAction")
public class AnnuityDetailIngestionAction extends AbstractJsonStreamIngestionAction<AnnuityEmployeeDTO, AnnuityEmployeeDetail> {

  private final AnnuityEmployeeMapper employeeMapper;
  private final AnnuityEmployeeBatchRepository batchRepository;

  public AnnuityDetailIngestionAction(
    FileIntegrationGateway fileGateway,
    ObjectMapper objectMapper,
    PlatformTransactionManager txManager,
    AnnuityEmployeeMapper employeeMapper,
    AnnuityEmployeeBatchRepository batchRepository) {
    super(fileGateway, objectMapper, txManager, AnnuityEmployeeDTO.class);
    this.employeeMapper = employeeMapper;
    this.batchRepository = batchRepository;
  }

  @Override
  public String actionName() {
    return "annuityDetailIngestionAction";
  }

  @Override
  protected AnnuityEmployeeDetail mapToEntity(ApplicationId appId, AnnuityEmployeeDTO dto, Map<String, Object> params, int rowIndex) {
    return employeeMapper.mapToEntity(dto, appId, rowIndex);
  }

  @Override
  protected void saveBatch(List<AnnuityEmployeeDetail> details) {
    if (details.isEmpty()) {
      return;
    }
    ApplicationId appId = details.get(0).batchId() == null ? null
      : new ApplicationId(details.get(0).batchId().value().replace("B-", ""));
    AnnuityEmployeeBatchId batchId = details.get(0).batchId();
    AnnuityEmployeeBatch batch = batchRepository.findByApplicationId(appId)
      .orElseGet(() -> AnnuityEmployeeBatch.create(batchId, appId, details.size(),
        UserNo.of("SYSTEM")));
    details.forEach(batch::addDetail);
    batchRepository.save(batch);
  }

  @Override
  protected String extractTraceId(AnnuityEmployeeDetail entity) {
    return entity.idCardNo();
  }

  @Override
  protected int getBatchSize() {
    return 100;
  }
}
