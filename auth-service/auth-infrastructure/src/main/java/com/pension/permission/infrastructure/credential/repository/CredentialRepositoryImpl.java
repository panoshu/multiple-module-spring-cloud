package com.pension.permission.infrastructure.credential.repository;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.mybatisflex.core.query.QueryWrapper;
import com.pension.permission.domain.credential.aggregate.Credential;
import com.pension.permission.domain.credential.enumeration.CredentialStatus;
import com.pension.permission.domain.credential.enumeration.CredentialType;
import com.pension.permission.domain.credential.repository.CredentialRepository;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.infrastructure.credential.converter.CredentialConverter;
import com.pension.permission.infrastructure.credential.entity.CredentialDO;
import com.pension.permission.infrastructure.credential.mapper.CredentialMapper;
import com.pension.permission.types.CredentialId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.pension.permission.infrastructure.credential.entity.table.CredentialDOTableDef.CREDENTIAL_DO;

/**
 * 凭证仓储实现.
 *
 * <p>负责 {@link Credential} 聚合根的持久化操作。领域事件不在 Repository 发布，
 * 由 {@code ApplicationService} 在编排时统一发布。</p>
 *
 * @author auth-service
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CredentialRepositoryImpl implements CredentialRepository {

  private final CredentialMapper credentialMapper;
  private final CredentialConverter converter;

  @Override
  public Optional<Credential> load(CredentialId id) {
    if (id == null) {
      return Optional.empty();
    }
    CredentialDO doObj = credentialMapper.selectOneById(id.value());
    return Optional.ofNullable(converter.toDomain(doObj));
  }

  @Override
  public void save(Credential credential) {
    if (credential == null) {
      throw new IllegalArgumentException("Credential 不能为空");
    }

    CredentialDO doObj = converter.toDO(credential);
    CredentialDO existing = credentialMapper.selectOneById(doObj.getId());

    if (existing == null) {
      credentialMapper.insert(doObj);
      log.debug("新增 Credential: credentialId={}", credential.id());
    } else {
      doObj.setVersion(existing.getVersion());
      credentialMapper.update(doObj);
      log.debug("更新 Credential: credentialId={}, version={}", credential.id(), credential.version());
    }
  }

  @Override
  public void delete(Credential aggregateRoot) {
    if (aggregateRoot == null) {
      return;
    }
    credentialMapper.deleteById(aggregateRoot.id().value());
    log.debug("删除 Credential: credentialId={}", aggregateRoot.id());
  }

  @Override
  public void deleteById(CredentialId id) {
    if (id == null) {
      return;
    }
    credentialMapper.deleteById(id.value());
    log.debug("根据 ID 删除 Credential: credentialId={}", id);
  }

  @Override
  public List<Credential> loadAll() {
    List<CredentialDO> doList = credentialMapper.selectAll();
    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public void streamByAppId(CredentialId id, Consumer<AggregateRoot<CredentialId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  @Override
  public List<Credential> findByOwner(CredentialOwner owner) {
    if (owner == null) {
      return List.of();
    }

    List<CredentialDO> doList = credentialMapper.selectListByQuery(
      QueryWrapper.create()
        .where(CREDENTIAL_DO.OWNER_TYPE.eq(converter.toOwnerType(owner)))
        .and(CREDENTIAL_DO.OWNER_ID.eq(converter.toOwnerId(owner)))
    );

    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public Optional<Credential> findByOwnerAndType(CredentialOwner owner, CredentialType type) {
    if (owner == null || type == null) {
      return Optional.empty();
    }

    CredentialDO doObj = credentialMapper.selectOneByQuery(
      QueryWrapper.create()
        .where(CREDENTIAL_DO.OWNER_TYPE.eq(converter.toOwnerType(owner)))
        .and(CREDENTIAL_DO.OWNER_ID.eq(converter.toOwnerId(owner)))
        .and(CREDENTIAL_DO.CREDENTIAL_TYPE.eq(type.name()))
    );

    return Optional.ofNullable(converter.toDomain(doObj));
  }

  @Override
  public List<Credential> findActiveCredentials(CredentialOwner owner) {
    if (owner == null) {
      return List.of();
    }

    List<CredentialDO> doList = credentialMapper.selectListByQuery(
      QueryWrapper.create()
        .where(CREDENTIAL_DO.OWNER_TYPE.eq(converter.toOwnerType(owner)))
        .and(CREDENTIAL_DO.OWNER_ID.eq(converter.toOwnerId(owner)))
        .and(CREDENTIAL_DO.STATUS.eq(CredentialStatus.ACTIVE.name()))
    );

    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public List<Credential> findUsableCredentials(CredentialOwner owner, AnnuityChannel channel) {
    if (owner == null || channel == null) {
      return List.of();
    }

    return findActiveCredentials(owner).stream()
      .filter(credential -> credential.applicableChannels().contains(channel))
      .toList();
  }

  @Override
  public List<Credential> findByType(CredentialType type) {
    if (type == null) {
      return List.of();
    }

    List<CredentialDO> doList = credentialMapper.selectListByQuery(
      QueryWrapper.create()
        .where(CREDENTIAL_DO.CREDENTIAL_TYPE.eq(type.name()))
    );

    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }
}
