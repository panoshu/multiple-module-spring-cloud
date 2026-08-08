package com.example.core.infrastructure.business.repository;

import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.repository.FormRepository;
import com.example.core.infrastructure.business.converter.BusinessFormConverter;
import com.example.core.infrastructure.business.entity.BusinessFormDO;
import com.example.core.infrastructure.business.mapper.BusinessFormMapper;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.FormId;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static com.example.core.infrastructure.business.entity.table.BusinessApplicationDOTableDef.BUSINESS_APPLICATION_DO;
import static com.example.core.infrastructure.business.entity.table.BusinessFormDOTableDef.BUSINESS_FORM_DO;

/**
 * 业务表单仓储基类实现
 * <p>
 * 继承 {@link AbstractBusinessRepository}，封装通用的 CRUD 与领域事件发布逻辑。
 * 本类<b>不标注 {@code @Repository}</b>，由具体业务服务继承本类并标注 {@code @Repository}。
 *
 * @author core-kernel
 * @since 2026/8/8
 */
public class BusinessFormRepositoryImpl
  extends AbstractBusinessRepository<BusinessForm, FormId, BusinessFormDO>
  implements FormRepository {

  protected final BusinessFormMapper mapper;
  protected final BusinessFormConverter converter;

  public BusinessFormRepositoryImpl(ApplicationEventPublisher eventPublisher,
                                    BusinessFormMapper mapper,
                                    BusinessFormConverter converter) {
    super(eventPublisher);
    this.mapper = mapper;
    this.converter = converter;
  }

  @Override
  protected BaseMapper<BusinessFormDO> mapper() {
    return mapper;
  }

  @Override
  protected BusinessFormDO toDO(BusinessForm aggregate) {
    return converter.toDO(aggregate);
  }

  @Override
  protected BusinessForm toDomain(BusinessFormDO aDo) {
    return converter.toDomain(aDo);
  }

  @Override
  protected Object doId(BusinessFormDO aDo) {
    return aDo.getId();
  }

  /**
   * 通过申请单 ID 反查表单：t_business_application.form_id → t_business_form.id
   */
  @Override
  public Optional<BusinessForm> findByApplicationId(ApplicationId applicationId) {
    if (applicationId == null) {
      return Optional.empty();
    }
    BusinessFormDO aDo = mapper.selectOneByQuery(
      QueryWrapper.create()
        .where(BUSINESS_FORM_DO.ID.in(
          QueryWrapper.create()
            .select(BUSINESS_APPLICATION_DO.FORM_ID)
            .from(BUSINESS_APPLICATION_DO)
            .where(BUSINESS_APPLICATION_DO.ID.eq(applicationId.value()))
        ))
    );
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }
}
