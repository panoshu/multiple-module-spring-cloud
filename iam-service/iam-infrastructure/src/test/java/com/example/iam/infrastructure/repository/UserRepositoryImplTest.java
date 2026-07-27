package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.domain.authentication.repository.UserRepository;
import com.example.iam.infrastructure.IamInfrastructureTestApplication;
import com.example.iam.types.UserId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserRepositoryImpl} 集成测试。
 *
 * <p>使用 H2 内存数据库(PostgreSQL 兼容模式)验证 UserRepository 的 CRUD 行为,
 * 包括用户与档案的 1:1 共享主键持久化、按登录名查询、软删除等核心场景。
 *
 * <p>每个测试方法使用 {@link Transactional} 自动回滚,避免数据污染。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("UserRepositoryImpl 集成测试")
@SpringBootTest(classes = IamInfrastructureTestApplication.class)
@ActiveProfiles("test")
@Transactional
class UserRepositoryImplTest {

    @Autowired
    private UserRepository userRepository;

    private static final Long USER_ID_VALUE = 10001L;
    private static final Long ALT_USER_ID_VALUE = 10002L;
    private static final ChannelType CHANNEL_TYPE = ChannelType.INTERNET;
    private static final String LOGIN_NAME = "alice";
    private static final String DISPLAY_NAME = "爱丽丝";
    private static final UserNo OPERATOR = UserNo.of("U-ADMIN-001");

    @Nested
    @DisplayName("save + load: 新建与读取")
    class SaveAndLoadTest {

        @Test
        @DisplayName("新建用户(无档案)后能通过 ID 加载,关键字段一致")
        void shouldSaveNewUserAndLoadById() {
            User user = User.create(
                    UserId.of(USER_ID_VALUE), CHANNEL_TYPE,
                    LOGIN_NAME, DISPLAY_NAME, OPERATOR);

            userRepository.save(user);

            Optional<User> loaded = userRepository.load(UserId.of(USER_ID_VALUE));

            assertThat(loaded).isPresent();
            User actual = loaded.get();
            assertThat(actual.id().value()).isEqualTo(USER_ID_VALUE);
            assertThat(actual.channelType()).isEqualTo(CHANNEL_TYPE);
            assertThat(actual.loginName()).isEqualTo(LOGIN_NAME);
            assertThat(actual.displayName()).isEqualTo(DISPLAY_NAME);
            assertThat(actual.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(actual.profile()).isNull();
            assertThat(actual.createdBy()).isEqualTo(OPERATOR);
            assertThat(actual.version()).isNotNull();
        }

        @Test
        @DisplayName("load 不存在的 ID 返回 empty")
        void shouldReturnEmptyWhenLoadNonexistentId() {
            Optional<User> loaded = userRepository.load(UserId.of(999999L));

            assertThat(loaded).isEmpty();
        }

        @Test
        @DisplayName("load 传入 null 返回 empty")
        void shouldReturnEmptyWhenLoadNullId() {
            Optional<User> loaded = userRepository.load(null);

            assertThat(loaded).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByLoginName: 按登录名查询")
    class FindByLoginNameTest {

        @Test
        @DisplayName("按 loginName + channelType 命中已存在用户")
        void shouldFindByLoginNameAndChannel() {
            User user = User.create(
                    UserId.of(USER_ID_VALUE), CHANNEL_TYPE,
                    LOGIN_NAME, DISPLAY_NAME, OPERATOR);
            userRepository.save(user);

            Optional<User> found = userRepository.findByLoginName(LOGIN_NAME, CHANNEL_TYPE);

            assertThat(found).isPresent();
            assertThat(found.get().loginName()).isEqualTo(LOGIN_NAME);
            assertThat(found.get().channelType()).isEqualTo(CHANNEL_TYPE);
        }

        @Test
        @DisplayName("loginName 相同但 channelType 不同时返回 empty")
        void shouldNotFindWhenChannelMismatch() {
            User user = User.create(
                    UserId.of(USER_ID_VALUE), CHANNEL_TYPE,
                    LOGIN_NAME, DISPLAY_NAME, OPERATOR);
            userRepository.save(user);

            Optional<User> found = userRepository.findByLoginName(LOGIN_NAME, ChannelType.HQ);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("existsByLoginName: 存在返回 true,不存在返回 false")
        void shouldCheckExistenceByLoginName() {
            User user = User.create(
                    UserId.of(USER_ID_VALUE), CHANNEL_TYPE,
                    LOGIN_NAME, DISPLAY_NAME, OPERATOR);
            userRepository.save(user);

            assertThat(userRepository.existsByLoginName(LOGIN_NAME, CHANNEL_TYPE)).isTrue();
            assertThat(userRepository.existsByLoginName("nonexistent", CHANNEL_TYPE)).isFalse();
        }
    }

    @Nested
    @DisplayName("loadAll: 全量加载")
    class LoadAllTest {

        @Test
        @DisplayName("加载所有用户,数量与保存一致")
        void shouldLoadAllUsers() {
            User u1 = User.create(UserId.of(USER_ID_VALUE), CHANNEL_TYPE,
                    "user1", "用户1", OPERATOR);
            User u2 = User.create(UserId.of(ALT_USER_ID_VALUE), ChannelType.HQ,
                    "user2", "用户2", OPERATOR);
            userRepository.save(u1);
            userRepository.save(u2);

            List<User> all = userRepository.loadAll();

            assertThat(all).hasSize(2);
            assertThat(all).extracting(u -> u.id().value())
                    .containsExactlyInAnyOrder(USER_ID_VALUE, ALT_USER_ID_VALUE);
        }
    }

    @Nested
    @DisplayName("delete: 软删除")
    class DeleteTest {

        @Test
        @DisplayName("delete 后 load 返回 empty(软删除生效)")
        void shouldSoftDeleteUser() {
            User user = User.create(
                    UserId.of(USER_ID_VALUE), CHANNEL_TYPE,
                    LOGIN_NAME, DISPLAY_NAME, OPERATOR);
            userRepository.save(user);

            userRepository.delete(user);

            assertThat(userRepository.load(UserId.of(USER_ID_VALUE))).isEmpty();
        }

        @Test
        @DisplayName("deleteById 后 load 返回 empty")
        void shouldSoftDeleteById() {
            User user = User.create(
                    UserId.of(USER_ID_VALUE), CHANNEL_TYPE,
                    LOGIN_NAME, DISPLAY_NAME, OPERATOR);
            userRepository.save(user);

            userRepository.deleteById(UserId.of(USER_ID_VALUE));

            assertThat(userRepository.load(UserId.of(USER_ID_VALUE))).isEmpty();
            assertThat(userRepository.existsByLoginName(LOGIN_NAME, CHANNEL_TYPE)).isFalse();
        }

        @Test
        @DisplayName("delete null 用户不抛异常")
        void shouldNotThrowWhenDeleteNull() {
            userRepository.delete(null);
            userRepository.deleteById(null);
        }
    }
}
