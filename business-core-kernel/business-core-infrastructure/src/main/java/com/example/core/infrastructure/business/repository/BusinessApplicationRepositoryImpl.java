package com.example.core.infrastructure.business.repository;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.gateway.ParsedFileReferenceGateway;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.infrastructure.business.converter.BusinessApplicationConverter;
import com.example.core.infrastructure.business.entity.BusinessApplicationDO;
import com.example.core.infrastructure.business.mapper.BusinessApplicationMapper;
import com.example.core.types.ApplicationId;
import com.example.core.types.BatchId;
import com.example.core.types.FileRefId;
import com.mybatisflex.core.BaseMapper;
import com.example.shared.domain.event.EventBus;

import java.util.List;
import java.util.Optional;

import static com.example.core.infrastructure.business.entity.table.BusinessApplicationDOTableDef.BUSINESS_APPLICATION_DO;

/**
 * 业务申请单仓储基类实现
 * <p>
 * 继承 {@link AbstractBusinessRepository}，封装通用的 CRUD 与领域事件发布逻辑。
 * 本类<b>不标注 {@code @Repository}</b>，由 {@code business-core-starter} 统一注册为
 * Spring Bean；宿主可通过声明同类型 Bean 覆盖。按文件凭证反查申请单委托业务服务提供的
 * {@link ParsedFileReferenceGateway}，不再要求宿主继承本类。
 *
 * @author core-kernel
 * @since 2026/8/8
 */
public class BusinessApplicationRepositoryImpl
  extends AbstractBusinessRepository<BusinessApplication, ApplicationId, BusinessApplicationDO>
  implements ApplicationRepository {

  protected final BusinessApplicationMapper mapper;
  protected final BusinessApplicationConverter converter;
  private final ParsedFileReferenceGateway parsedFileReferenceGateway;

  public BusinessApplicationRepositoryImpl(EventBus eventBus,
                                           BusinessApplicationMapper mapper,
                                           BusinessApplicationConverter converter) {
    this(eventBus, mapper, converter, null);
  }

  public BusinessApplicationRepositoryImpl(EventBus eventBus,
                                           BusinessApplicationMapper mapper,
                                           BusinessApplicationConverter converter,
                                           ParsedFileReferenceGateway parsedFileReferenceGateway) {
    super(eventBus);
    this.mapper = mapper;
    this.converter = converter;
    this.parsedFileReferenceGateway = parsedFileReferenceGateway;
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

  @Override
  protected Integer doVersion(BusinessApplicationDO aDo) {
    return aDo.getVersion();
  }

  @Override
  protected void doVersion(BusinessApplicationDO aDo, Integer version) {
    aDo.setVersion(version);
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

  /**
   * 通过文件任务 ID 反查申请单：委托宿主提供的解析文件引用存储。
   * <p>
   * 未装配 {@link ParsedFileReferenceGateway} 的宿主仍保留契约默认的显式失败语义，
   * 避免在缺少关联存储时静默返回空结果。
   */
  @Override
  public Optional<BusinessApplication> findByFileTaskId(String fileTaskId) {
    if (fileTaskId == null || fileTaskId.isBlank()) {
      return Optional.empty();
    }
    if (parsedFileReferenceGateway == null) {
      return ApplicationRepository.super.findByFileTaskId(fileTaskId);
    }
    return parsedFileReferenceGateway.findByFileId(new FileRefId(fileTaskId)).flatMap(this::load);
  }

}
