package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.infrastructure.converter.CredentialConverter;
import com.example.iam.infrastructure.entity.CredentialDO;
import com.example.iam.infrastructure.mapper.CredentialMapper;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.iam.infrastructure.entity.table.CredentialDOTableDef.CREDENTIAL_DO;

/**
 * 凭据聚合根仓储实现。
 *
 * <p>负责 {@link Credential} 的持久化操作。一个用户可同时持有多种类型的凭据(每种类型一条记录)。
 *
 * <p>时间戳由应用层管理,Converter 直接从领域对象映射到 DO,不使用 ORM 自动填充。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CredentialRepositoryImpl implements CredentialRepository {

    private final CredentialMapper credentialMapper;
    private final CredentialConverter converter;
    private final DomainEventPublisher eventPublisher;

    @Override
    public Optional<Credential> load(CredentialId id) {
        if (id == null) {
            return Optional.empty();
        }
        CredentialDO credentialDO = credentialMapper.selectOneById(id.value());
        return Optional.ofNullable(converter.toDomain(credentialDO));
    }

    @Override
    public void save(Credential credential) {
        if (credential == null) {
            throw new IllegalArgumentException("凭据不能为空");
        }
        CredentialDO credentialDO = converter.toDO(credential);
        boolean isInsert = credentialMapper.selectOneById(credential.id().value()) == null;
        if (isInsert) {
            credentialMapper.insert(credentialDO);
            log.debug("新增凭据: credentialId={}, ownerId={}, type={}",
                    credential.id(), credential.ownerId(), credential.credentialType());
        } else {
            credentialMapper.update(credentialDO);
            log.debug("更新凭据: credentialId={}, version={}", credential.id(), credential.version());
        }
        eventPublisher.publishFor(credential);
    }

    @Override
    public void delete(Credential credential) {
        if (credential == null) {
            return;
        }
        CredentialDO credentialDO = credentialMapper.selectOneById(credential.id().value());
        if (credentialDO != null) {
            credentialMapper.delete(credentialDO);
        }
        log.debug("删除凭据: credentialId={}", credential.id());
    }

    @Override
    public void deleteById(CredentialId id) {
        if (id == null) {
            return;
        }
        credentialMapper.deleteById(id.value());
        log.debug("根据ID删除凭据: credentialId={}", id);
    }

    @Override
    public List<Credential> loadAll() {
        List<CredentialDO> credentialDOs = credentialMapper.selectAll();
        return credentialDOs.stream()
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
    public Optional<Credential> findActive(Long ownerId, String ownerType, CredentialType credentialType) {
        if (ownerId == null || ownerType == null || credentialType == null) {
            return Optional.empty();
        }
        CredentialDO credentialDO = credentialMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(CREDENTIAL_DO.OWNER_ID.eq(ownerId))
                        .and(CREDENTIAL_DO.OWNER_TYPE.eq(ownerType))
                        .and(CREDENTIAL_DO.CREDENTIAL_TYPE.eq(credentialType.name()))
                        .and(CREDENTIAL_DO.STATUS.eq(CredentialStatus.ACTIVE.name()))
        );
        return Optional.ofNullable(converter.toDomain(credentialDO));
    }

    @Override
    public List<Credential> findAllByOwner(Long ownerId, String ownerType) {
        if (ownerId == null || ownerType == null) {
            return List.of();
        }
        List<CredentialDO> credentialDOs = credentialMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(CREDENTIAL_DO.OWNER_ID.eq(ownerId))
                        .and(CREDENTIAL_DO.OWNER_TYPE.eq(ownerType))
                        .and(CREDENTIAL_DO.STATUS.in(
                                CredentialStatus.ACTIVE.name(),
                                CredentialStatus.EXPIRED.name()))
        );
        return credentialDOs.stream()
                .map(converter::toDomain)
                .toList();
    }
}
