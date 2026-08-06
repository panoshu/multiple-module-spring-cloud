package com.pension.permission.infrastructure.user.repository;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.identifier.id.UserNo;
import com.mybatisflex.core.query.QueryWrapper;
import com.pension.permission.domain.user.aggregate.UserAggregate;
import com.pension.permission.domain.user.repository.UserRepository;
import com.pension.permission.infrastructure.user.converter.UserConverter;
import com.pension.permission.infrastructure.user.entity.UserDO;
import com.pension.permission.infrastructure.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.pension.permission.infrastructure.user.entity.table.UserDOTableDef.USER_DO;

/**
 * 用户仓储实现.
 *
 * <p>负责 {@link UserAggregate} 聚合根的持久化操作。领域事件不在 Repository 发布，
 * 由 {@code ApplicationService} 在编排时统一发布。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

  private final UserMapper userMapper;
  private final UserConverter converter;

  @Override
  public Optional<UserAggregate> load(UserNo id) {
    if (id == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(userMapper.selectOneById(id.value()))
      .map(converter::toDomain);
  }

  @Override
  public void save(UserAggregate user) {
    if (user == null) {
      throw new IllegalArgumentException("UserAggregate 不能为空");
    }

    UserDO doObj = converter.toDO(user);
    UserDO existing = userMapper.selectOneById(doObj.getId());

    if (existing == null) {
      userMapper.insert(doObj);
      log.debug("新增 UserAggregate: userId={}", user.id());
    } else {
      doObj.setVersion(existing.getVersion());
      userMapper.update(doObj);
      log.debug("更新 UserAggregate: userId={}, version={}", user.id(), user.version());
    }
  }

  @Override
  public void delete(UserAggregate aggregateRoot) {
    if (aggregateRoot == null) {
      return;
    }
    userMapper.deleteById(aggregateRoot.id().value());
    log.debug("删除 UserAggregate: userId={}", aggregateRoot.id());
  }

  @Override
  public void deleteById(UserNo id) {
    if (id == null) {
      return;
    }
    userMapper.deleteById(id.value());
    log.debug("根据 ID 删除 UserAggregate: userId={}", id);
  }

  @Override
  public List<UserAggregate> loadAll() {
    List<UserDO> doList = userMapper.selectAll();
    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public void streamByAppId(UserNo id, Consumer<AggregateRoot<UserNo>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  @Override
  public Optional<UserAggregate> findByMobile(Mobile mobile) {
    if (mobile == null) {
      return Optional.empty();
    }

    UserDO doObj = userMapper.selectOneByQuery(
      QueryWrapper.create()
        .where(USER_DO.MOBILE.eq(mobile.value()))
    );

    return Optional.ofNullable(converter.toDomain(doObj));
  }
}
