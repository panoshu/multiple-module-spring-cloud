package com.example.core.infrastructure.business.repository;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.infrastructure.business.converter.BusinessApplicationConverter;
import com.example.core.infrastructure.business.entity.BusinessApplicationDO;
import com.example.core.infrastructure.business.mapper.BusinessApplicationMapper;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.BatchId;
import com.mybatisflex.core.BaseMapper;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static com.example.core.infrastructure.business.entity.table.BusinessApplicationDOTableDef.BUSINESS_APPLICATION_DO;

/**
 * 业务申请单仓储基类实现
 * <p>
 * 继承 {@link AbstractBusinessRepository}，封装通用的 CRUD 与领域事件发布逻辑。
 * 本类<b>不标注 {@code @Repository}</b>，由具体业务服务（如 annuity-service）继承本类
 * 并标注 {@code @Repository}，覆写 {@link #findByFileTaskId} / {@link #findByBatchId}
 * 等业务专属查询方法。
 *
 * @author core-kernel
 * @since 2026/8/8
 */
public class BusinessApplicationRepositoryImpl
  extends AbstractBusinessRepository<BusinessApplication, ApplicationId, BusinessApplicationDO>
  implements ApplicationRepository {

  protected final BusinessApplicationMapper mapper;
  protected final BusinessApplicationConverter converter;

  public BusinessApplicationRepositoryImpl(ApplicationEventPublisher eventPublisher,
                                           BusinessApplicationMapper mapper,
                                           BusinessApplicationConverter converter) {
    super(eventPublisher);
    this.mapper = mapper;
    this.converter = converter;
  }

  @Override
  protected BaseMapper<BusinessApplicationDO> mapper() {
    return mapper;
  }

  @Override
  protected BusinessApplicationDO toDO(BusinessApplication aggregate) {
    return converter.toDO(aggregate);
  }

  @Override
  protected BusinessApplication toDomain(BusinessApplicationDO aDo) {
    return converter.toDomain(aDo);
  }

  @Override
  protected Object doId(BusinessApplicationDO aDo) {
    return aDo.getId();
  }

  /**
   * 通过批次 ID 查询该批次下所有业务申请单。
   * <p>
   * 本类提供了基于 {@code batch_id} 列的通用实现，业务服务可直接继承使用。
   *
   * @param batchId 批次 ID
   * @return 该批次下的所有业务申请单列表
   */
  @Override
  public List<BusinessApplication> findByBatchId(BatchId batchId) {
    if (batchId == null) {
      return List.of();
    }
    return mapper.selectListByQuery(
        com.mybatisflex.core.query.QueryWrapper.create()
          .where(BUSINESS_APPLICATION_DO.BATCH_ID.eq(batchId.value()))
      ).stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public Optional<BusinessApplication> findByFileTaskId(String fileTaskId) {
    if (fileTaskId == null || fileTaskId.isBlank()) {
      return Optional.empty();
    }
    BusinessApplicationDO aDo = mapper.selectOneByQuery(
      com.mybatisflex.core.query.QueryWrapper.create()
        .where(BUSINESS_APPLICATION_DO.PARSED_JSON_FILE_ID.eq(fileTaskId))
    );
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }
}
