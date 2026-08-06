package com.pension.permission.infrastructure.channel.repository;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.identifier.id.CustomerNo;
import com.mybatisflex.core.query.QueryWrapper;
import com.pension.permission.domain.channel.aggregate.CustomerChannelEntitlement;
import com.pension.permission.domain.channel.repository.CustomerChannelEntitlementRepository;
import com.pension.permission.infrastructure.channel.converter.CustomerChannelEntitlementConverter;
import com.pension.permission.infrastructure.channel.entity.CustomerChannelEntitlementDO;
import com.pension.permission.infrastructure.channel.mapper.CustomerChannelEntitlementMapper;
import com.pension.permission.types.CustomerChannelEntitlementId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.pension.permission.infrastructure.channel.entity.table.CustomerChannelEntitlementDOTableDef.CUSTOMER_CHANNEL_ENTITLEMENT_DO;

/**
 * 客户渠道开通记录仓储实现.
 *
 * <p>负责 {@link CustomerChannelEntitlement} 聚合根的持久化操作。领域事件不在 Repository 发布，
 * 由 {@code CustomerChannelEntitlementService} 在编排时统一发布。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CustomerChannelEntitlementRepositoryImpl implements CustomerChannelEntitlementRepository {

  private final CustomerChannelEntitlementMapper mapper;
  private final CustomerChannelEntitlementConverter converter;

  @Override
  public Optional<CustomerChannelEntitlement> load(CustomerChannelEntitlementId id) {
    if (id == null) {
      return Optional.empty();
    }
    CustomerChannelEntitlementDO doObj = mapper.selectOneById(id.value());
    return Optional.ofNullable(converter.toDomain(doObj));
  }

  @Override
  public void save(CustomerChannelEntitlement entitlement) {
    if (entitlement == null) {
      throw new IllegalArgumentException("CustomerChannelEntitlement 不能为空");
    }
    CustomerChannelEntitlementDO doObj = converter.toDO(entitlement);
    CustomerChannelEntitlementDO existing = mapper.selectOneById(doObj.getId());
    if (existing == null) {
      mapper.insert(doObj);
      log.debug("新增 CustomerChannelEntitlement: id={}, customerNo={}",
        entitlement.id(), entitlement.customerNo());
    } else {
      doObj.setVersion(existing.getVersion());
      mapper.update(doObj);
      log.debug("更新 CustomerChannelEntitlement: id={}, customerNo={}, version={}",
        entitlement.id(), entitlement.customerNo(), entitlement.version());
    }
  }

  @Override
  public void delete(CustomerChannelEntitlement aggregateRoot) {
    if (aggregateRoot == null) {
      return;
    }
    mapper.deleteById(aggregateRoot.id().value());
    log.debug("删除 CustomerChannelEntitlement: id={}", aggregateRoot.id());
  }

  @Override
  public void deleteById(CustomerChannelEntitlementId id) {
    if (id == null) {
      return;
    }
    mapper.deleteById(id.value());
    log.debug("根据 ID 删除 CustomerChannelEntitlement: id={}", id);
  }

  @Override
  public List<CustomerChannelEntitlement> loadAll() {
    List<CustomerChannelEntitlementDO> doList = mapper.selectAll();
    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public void streamByAppId(
    CustomerChannelEntitlementId id,
    Consumer<AggregateRoot<CustomerChannelEntitlementId>> processor
  ) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  @Override
  public Optional<CustomerChannelEntitlement> findByCustomer(CustomerNo customerNo) {
    if (customerNo == null) {
      return Optional.empty();
    }
    CustomerChannelEntitlementDO doObj = mapper.selectOneByQuery(
      QueryWrapper.create()
        .where(CUSTOMER_CHANNEL_ENTITLEMENT_DO.CUSTOMER_NO.eq(customerNo.value()))
        .and(CUSTOMER_CHANNEL_ENTITLEMENT_DO.DELETED.eq(false))
        .limit(1)
    );
    return Optional.ofNullable(converter.toDomain(doObj));
  }
}
