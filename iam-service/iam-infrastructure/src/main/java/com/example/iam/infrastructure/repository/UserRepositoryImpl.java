package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.repository.UserRepository;
import com.example.iam.infrastructure.converter.UserConverter;
import com.example.iam.infrastructure.entity.UserDO;
import com.example.iam.infrastructure.entity.UserProfileDO;
import com.example.iam.infrastructure.mapper.UserMapper;
import com.example.iam.infrastructure.mapper.UserProfileMapper;
import com.example.iam.types.UserId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.iam.infrastructure.entity.table.UserDOTableDef.USER_DO;

/**
 * 用户聚合根仓储实现。
 *
 * <p>负责 {@link User}+{@link com.example.iam.domain.authentication.aggregate.entity.UserProfile}
 * 子实体的持久化操作。User 与 UserProfile 为 1:1 关系,共享主键(User.id = UserProfile.userId)。
 *
 * <p>时间戳由应用层管理,Converter 直接从领域对象映射到 DO,不使用 ORM 自动填充。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final UserConverter converter;
    private final DomainEventPublisher eventPublisher;

    @Override
    public Optional<User> load(UserId id) {
        if (id == null) {
            return Optional.empty();
        }
        UserDO userDO = userMapper.selectOneById(id.value());
        if (userDO == null) {
            return Optional.empty();
        }
        UserProfileDO profileDO = profileMapper.selectOneById(id.value());
        return Optional.ofNullable(converter.toDomain(userDO, profileDO));
    }

    @Override
    public void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("用户不能为空");
        }
        UserDO userDO = converter.toDO(user);
        boolean isInsert = userMapper.selectOneById(user.id().value()) == null;
        if (isInsert) {
            userMapper.insert(userDO);
            log.debug("新增用户: userId={}, loginName={}", user.id(), user.loginName());
        } else {
            userMapper.update(userDO);
            log.debug("更新用户: userId={}, version={}", user.id(), user.version());
        }
        saveProfile(user);
        eventPublisher.publishFor(user);
    }

    @Override
    public void delete(User user) {
        if (user == null) {
            return;
        }
        // 逻辑删除档案再删除用户(由 @Column(isLogicDelete) 自动处理)
        if (user.profile() != null) {
            UserProfileDO profileDO = profileMapper.selectOneById(user.id().value());
            if (profileDO != null) {
                profileMapper.delete(profileDO);
            }
        }
        UserDO userDO = userMapper.selectOneById(user.id().value());
        if (userDO != null) {
            userMapper.delete(userDO);
        }
        log.debug("删除用户: userId={}", user.id());
    }

    @Override
    public void deleteById(UserId id) {
        if (id == null) {
            return;
        }
        UserProfileDO profileDO = profileMapper.selectOneById(id.value());
        if (profileDO != null) {
            profileMapper.delete(profileDO);
        }
        UserDO userDO = userMapper.selectOneById(id.value());
        if (userDO != null) {
            userMapper.delete(userDO);
        }
        log.debug("根据ID删除用户: userId={}", id);
    }

    @Override
    public List<User> loadAll() {
        List<UserDO> userDOs = userMapper.selectAll();
        return userDOs.stream()
                .map(userDO -> {
                    UserProfileDO profileDO = profileMapper.selectOneById(userDO.getId());
                    return converter.toDomain(userDO, profileDO);
                })
                .toList();
    }

    @Override
    public void streamByAppId(UserId id, Consumer<AggregateRoot<UserId>> processor) {
        if (id == null || processor == null) {
            return;
        }
        load(id).ifPresent(processor);
    }

    @Override
    public Optional<User> findByLoginName(String loginName, ChannelType channelType) {
        if (loginName == null || channelType == null) {
            return Optional.empty();
        }
        UserDO userDO = userMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(USER_DO.LOGIN_NAME.eq(loginName))
                        .and(USER_DO.CHANNEL_TYPE.eq(channelType.name()))
        );
        if (userDO == null) {
            return Optional.empty();
        }
        UserProfileDO profileDO = profileMapper.selectOneById(userDO.getId());
        return Optional.ofNullable(converter.toDomain(userDO, profileDO));
    }

    @Override
    public boolean existsByLoginName(String loginName, ChannelType channelType) {
        if (loginName == null || channelType == null) {
            return false;
        }
        return userMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(USER_DO.LOGIN_NAME.eq(loginName))
                        .and(USER_DO.CHANNEL_TYPE.eq(channelType.name()))
        ) > 0;
    }

    /**
     * 保存用户档案(1:1 关联,通过 userId 共享主键)。
     */
    private void saveProfile(User user) {
        if (user.profile() == null) {
            return;
        }
        UserProfileDO profileDO = converter.toProfileDO(user.profile());
        boolean isInsert = profileMapper.selectOneById(user.id().value()) == null;
        if (isInsert) {
            profileMapper.insert(profileDO);
        } else {
            profileMapper.update(profileDO);
        }
    }
}
