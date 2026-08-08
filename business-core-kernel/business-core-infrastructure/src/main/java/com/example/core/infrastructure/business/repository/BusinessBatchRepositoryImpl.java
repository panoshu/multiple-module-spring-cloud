package com.example.core.infrastructure.business.repository;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.core.infrastructure.business.converter.BusinessBatchConverter;
import com.example.core.infrastructure.business.entity.BusinessBatchDO;
import com.example.core.infrastructure.business.mapper.BusinessBatchMapper;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.BatchId;
import com.example.shared.identifier.id.FormId;
import com.example.shared.identifier.id.PlanNo;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static com.example.core.infrastructure.business.entity.table.BusinessApplicationDOTableDef.BUSINESS_APPLICATION_DO;
import static com.example.core.infrastructure.business.entity.table.BusinessBatchDOTableDef.BUSINESS_BATCH_DO;
import static com.example.core.infrastructure.business.entity.table.BusinessFormDOTableDef.BUSINESS_FORM_DO;

/**
 * 业务批次仓储基类实现
 * <p>
 * 继承 {@link AbstractBusinessRepository}，封装通用的 CRUD 与领域事件发布逻辑。
 * 本类<b>不标注 {@code @Repository}</b>，由具体业务服务继承本类并标注 {@code @Repository}。
 *
 * @author core-kernel
 * @since 2026/8/8
 */
public class BusinessBatchRepositoryImpl
  extends AbstractBusinessRepository<BusinessBatch, BatchId, BusinessBatchDO>
  implements BatchRepository {

  protected final BusinessBatchMapper mapper;
  protected final BusinessBatchConverter converter;

  public BusinessBatchRepositoryImpl(ApplicationEventPublisher eventPublisher,
                                     BusinessBatchMapper mapper,
                                     BusinessBatchConverter converter) {
    super(eventPublisher);
    this.mapper = mapper;
    this.converter = converter;
  }

  @Override
  protected BaseMapper<BusinessBatchDO> mapper() {
    return mapper;
  }

  @Override
  protected BusinessBatchDO toDO(BusinessBatch aggregate) {
    return converter.toDO(aggregate);
  }

  @Override
  protected BusinessBatch toDomain(BusinessBatchDO aDo) {
    return converter.toDomain(aDo);
  }

  @Override
  protected Object doId(BusinessBatchDO aDo) {
    return aDo.getId();
  }

  /**
   * 通过表单 ID 反查批次：t_business_form.batch_id → t_business_batch.id
   */
  @Override
  public Optional<BusinessBatch> findByFormId(FormId formId) {
    if (formId == null) {
      return Optional.empty();
    }
    BusinessBatchDO aDo = mapper.selectOneByQuery(
      QueryWrapper.create()
        .where(BUSINESS_BATCH_DO.ID.in(
          QueryWrapper.create()
            .select(BUSINESS_FORM_DO.BATCH_ID)
            .from(BUSINESS_FORM_DO)
            .where(BUSINESS_FORM_DO.ID.eq(formId.value()))
        ))
    );
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  /**
   * 通过申请单 ID 反查批次：t_business_application.batch_id → t_business_batch.id
   */
  @Override
  public Optional<BusinessBatch> findByApplicationId(ApplicationId applicationId) {
    if (applicationId == null) {
      return Optional.empty();
    }
    BusinessBatchDO aDo = mapper.selectOneByQuery(
      QueryWrapper.create()
        .where(BUSINESS_BATCH_DO.ID.in(
          QueryWrapper.create()
            .select(BUSINESS_APPLICATION_DO.BATCH_ID)
            .from(BUSINESS_APPLICATION_DO)
            .where(BUSINESS_APPLICATION_DO.ID.eq(applicationId.value()))
        ))
    );
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  /**
   * 查询指定计划+业务类型的活跃批次。
   * <p>
   * 默认实现通过 {@code plan_no} + {@code business_type} + 状态过滤查询。
   * 业务服务可覆写本方法以提供更精确的查询逻辑。
   */
  @Override
  public Optional<BusinessBatch> findActive(PlanNo planNo, BusinessType businessType) {
    if (planNo == null || businessType == null) {
      return Optional.empty();
    }
    BusinessBatchDO aDo = mapper.selectOneByQuery(
      QueryWrapper.create()
        .where(BUSINESS_BATCH_DO.PLAN_NO.eq(planNo.value()))
        .and(BUSINESS_BATCH_DO.BUSINESS_TYPE.eq(businessType.name()))
        .and(BUSINESS_BATCH_DO.STATUS.in("CREATED", "PROCESSING"))
    );
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }
}
