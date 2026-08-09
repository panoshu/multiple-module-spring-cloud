package com.example.bff.shared.infrastructure.repository;

import com.example.bff.shared.infrastructure.entity.BffRouteConfigDO;
import com.example.bff.shared.infrastructure.mapper.BffRouteConfigMapper;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.BffRouteConfigEntry;
import com.example.bff.shared.route.BffRouteConfigRepository;
import com.example.bff.shared.route.ChannelScope;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.bff.shared.infrastructure.entity.table.BffRouteConfigDOTableDef.BFF_ROUTE_CONFIG_DO;

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

  public BffRouteConfigRepositoryImpl(BffRouteConfigMapper mapper) {
    this.mapper = mapper;
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
    return Optional.ofNullable(record).map(this::toRouteConfig);
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
    BffRouteConfigDO record = new BffRouteConfigDO();
    record.setBusinessType(config.businessType());
    record.setServiceName(config.serviceName());
    record.setChannelScope(config.channelScope().name());
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
      return;
    }
    record.setBusinessType(config.businessType());
    record.setServiceName(config.serviceName());
    record.setChannelScope(config.channelScope().name());
    record.setUpdatedBy(updatedBy);
    record.setUpdateTime(LocalDateTime.now());
    mapper.update(record);
  }

  @Override
  public void delete(Long id, String updatedBy) {
    BffRouteConfigDO record = mapper.selectOneById(id);
    if (record == null) {
      return;
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
    return Optional.ofNullable(record).map(this::toRouteConfig);
  }

  @Override
  public List<BffRouteConfig> findAll() {
    List<BffRouteConfigDO> records = mapper.selectListByQuery(
      QueryWrapper.create()
        .where(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
        .orderBy(BFF_ROUTE_CONFIG_DO.ID.asc())
    );
    return records.stream().map(this::toRouteConfig).toList();
  }

  @Override
  public List<BffRouteConfigEntry> findAllWithId() {
    List<BffRouteConfigDO> records = mapper.selectListByQuery(
      QueryWrapper.create()
        .where(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
        .orderBy(BFF_ROUTE_CONFIG_DO.ID.asc())
    );
    return records.stream()
      .map(record -> new BffRouteConfigEntry(record.getId(), toRouteConfig(record)))
      .toList();
  }

  private BffRouteConfig toRouteConfig(BffRouteConfigDO record) {
    return new BffRouteConfig(
      record.getBusinessType(),
      record.getServiceName(),
      ChannelScope.valueOf(record.getChannelScope())
    );
  }
}
