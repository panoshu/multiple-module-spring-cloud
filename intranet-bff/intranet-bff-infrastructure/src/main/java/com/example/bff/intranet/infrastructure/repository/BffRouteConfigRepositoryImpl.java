package com.example.bff.intranet.infrastructure.repository;

import com.example.bff.intranet.infrastructure.converter.BffRouteConfigConverter;
import com.example.bff.intranet.infrastructure.entity.BffRouteConfigDO;
import com.example.bff.intranet.infrastructure.mapper.BffRouteConfigMapper;
import com.example.bff.shared.errorcode.BffErrorCode;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.BffRouteConfigEntry;
import com.example.bff.shared.route.BffRouteConfigRepository;
import com.example.bff.shared.route.ChannelScope;
import com.example.shared.exception.BusinessException;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.bff.intranet.infrastructure.entity.table.BffRouteConfigDOTableDef.BFF_ROUTE_CONFIG_DO;

/**
 * BFF 路由配置 Repository 实现
 *
 * <p>查询逻辑：优先匹配指定渠道，未找到则回退到 ALL。
 *
 * @author bff
 */
@Repository
public class BffRouteConfigRepositoryImpl implements BffRouteConfigRepository {

  private final BffRouteConfigMapper mapper;
  private final BffRouteConfigConverter converter;

  public BffRouteConfigRepositoryImpl(BffRouteConfigMapper mapper, BffRouteConfigConverter converter) {
    this.mapper = mapper;
    this.converter = converter;
  }

  @Override
  public Optional<BffRouteConfig> findByBusinessType(String businessType, ChannelScope channelScope) {
    BffRouteConfigDO record = mapper.selectOneByQuery(
      QueryWrapper.create()
        .where(BFF_ROUTE_CONFIG_DO.BUSINESS_TYPE.eq(businessType))
        .and(BFF_ROUTE_CONFIG_DO.CHANNEL_SCOPE.eq(channelScope.name()))
        .and(BFF_ROUTE_CONFIG_DO.ENABLED.eq(true))
        .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
    );
    if (record == null) {
      record = mapper.selectOneByQuery(
        QueryWrapper.create()
          .where(BFF_ROUTE_CONFIG_DO.BUSINESS_TYPE.eq(businessType))
          .and(BFF_ROUTE_CONFIG_DO.CHANNEL_SCOPE.eq(ChannelScope.ALL.name()))
          .and(BFF_ROUTE_CONFIG_DO.ENABLED.eq(true))
          .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
      );
    }
    return Optional.ofNullable(record).map(converter::toDomain);
  }

  @Override
  public Set<String> findAllServiceNames() {
    List<BffRouteConfigDO> records = mapper.selectListByQuery(
      QueryWrapper.create()
        .select(BFF_ROUTE_CONFIG_DO.SERVICE_NAME)
        .where(BFF_ROUTE_CONFIG_DO.ENABLED.eq(true))
        .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
    );
    Set<String> names = new LinkedHashSet<>();
    for (BffRouteConfigDO record : records) {
      names.add(record.getServiceName());
    }
    return names;
  }

  @Override
  public Long save(BffRouteConfig config, String createdBy) {
    BffRouteConfigDO record = converter.toDO(config);
    record.setEnabled(true);
    record.setCreatedBy(createdBy);
    record.setCreateTime(LocalDateTime.now());
    record.setDeleted(false);
    record.setVersion(0);
    mapper.insert(record);
    return record.getId();
  }

  @Override
  public void update(Long id, BffRouteConfig config, String updatedBy) {
    BffRouteConfigDO record = mapper.selectOneById(id);
    if (record == null) {
      throw new BusinessException(BffErrorCode.ROUTE_CONFIG_NOT_FOUND)
        .withLogDetail("Route config not found for update, id: " + id);
    }
    BffRouteConfigDO updated = converter.toDO(config);
    updated.setId(id);
    updated.setCreatedBy(record.getCreatedBy());
    updated.setCreateTime(record.getCreateTime());
    updated.setEnabled(record.getEnabled());
    updated.setDeleted(record.getDeleted());
    updated.setVersion(record.getVersion());
    updated.setUpdatedBy(updatedBy);
    updated.setUpdateTime(LocalDateTime.now());
    mapper.update(updated);
  }

  @Override
  public void delete(Long id, String updatedBy) {
    BffRouteConfigDO record = mapper.selectOneById(id);
    if (record == null) {
      throw new BusinessException(BffErrorCode.ROUTE_CONFIG_NOT_FOUND)
        .withLogDetail("Route config not found for delete, id: " + id);
    }
    record.setDeleted(true);
    record.setUpdatedBy(updatedBy);
    record.setUpdateTime(LocalDateTime.now());
    mapper.update(record);
  }

  @Override
  public Optional<BffRouteConfig> findById(Long id) {
    BffRouteConfigDO record = mapper.selectOneByQuery(
      QueryWrapper.create()
        .where(BFF_ROUTE_CONFIG_DO.ID.eq(id))
        .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
    );
    return Optional.ofNullable(record).map(converter::toDomain);
  }

  @Override
  public List<BffRouteConfig> findAll() {
    List<BffRouteConfigDO> records = mapper.selectListByQuery(
      QueryWrapper.create()
        .where(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
        .orderBy(BFF_ROUTE_CONFIG_DO.ID.asc())
    );
    return records.stream().map(converter::toDomain).toList();
  }

  @Override
  public List<BffRouteConfigEntry> findAllWithId() {
    List<BffRouteConfigDO> records = mapper.selectListByQuery(
      QueryWrapper.create()
        .where(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
        .orderBy(BFF_ROUTE_CONFIG_DO.ID.asc())
    );
    return records.stream()
      .map(record -> new BffRouteConfigEntry(record.getId(), converter.toDomain(record)))
      .toList();
  }
}
