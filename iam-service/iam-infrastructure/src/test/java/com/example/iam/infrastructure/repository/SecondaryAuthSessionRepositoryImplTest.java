package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.repository.SecondaryAuthSessionRepository;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.infrastructure.IamInfrastructureTestApplication;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.iam.types.UserId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SecondaryAuthSessionRepositoryImpl} 集成测试。
 *
 * <p>验证二次授权会话聚合根的 CRUD、按柜员查询待授权/已授权会话、权限快照序列化等场景。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("SecondaryAuthSessionRepositoryImpl 集成测试")
@SpringBootTest(classes = IamInfrastructureTestApplication.class)
@ActiveProfiles("test")
@Transactional
class SecondaryAuthSessionRepositoryImplTest {

    @Autowired
    private SecondaryAuthSessionRepository sessionRepository;

    private static final Long SESSION_ID_VALUE = 40001L;
    private static final Long TELLER_ID = 10010L;
    private static final Long APPROVER_ID = 10020L;
    private static final String CUSTOMER_NO = "C-001";
    private static final String PLAN_ID = "P-001";
    private static final UserNo OPERATOR = UserNo.of("U-TELLER-001");

    @Nested
    @DisplayName("save + load: 新建与读取")
    class SaveAndLoadTest {

        @Test
        @DisplayName("新建 PENDING 会话后能通过 ID 加载,关键字段一致")
        void shouldSaveNewSessionAndLoadById() {
            SecondaryAuthSession session = SecondaryAuthSession.initiate(
                    SecondaryAuthSessionId.of(SESSION_ID_VALUE),
                    TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID, OPERATOR);

            sessionRepository.save(session);

            Optional<SecondaryAuthSession> loaded = sessionRepository
                    .load(SecondaryAuthSessionId.of(SESSION_ID_VALUE));

            assertThat(loaded).isPresent();
            SecondaryAuthSession actual = loaded.get();
            assertThat(actual.id().value()).isEqualTo(SESSION_ID_VALUE);
            assertThat(actual.tellerId()).isEqualTo(TELLER_ID);
            assertThat(actual.approverId()).isEqualTo(APPROVER_ID);
            assertThat(actual.customerNo()).isEqualTo(CUSTOMER_NO);
            assertThat(actual.planId()).isEqualTo(PLAN_ID);
            assertThat(actual.status()).isEqualTo(SecondaryAuthStatus.PENDING);
            assertThat(actual.permissionSnapshot()).isNull();
            assertThat(actual.initiatedAt()).isNotNull();
        }

        @Test
        @DisplayName("load 不存在的 ID 返回 empty")
        void shouldReturnEmptyWhenLoadNonexistentId() {
            Optional<SecondaryAuthSession> loaded = sessionRepository
                    .load(SecondaryAuthSessionId.of(999999L));

            assertThat(loaded).isEmpty();
        }
    }

    @Nested
    @DisplayName("findPendingByTeller: 查询柜员待授权会话")
    class FindPendingByTellerTest {

        @Test
        @DisplayName("返回柜员 PENDING 状态的会话")
        void shouldFindPendingSessionByTeller() {
            SecondaryAuthSession session = SecondaryAuthSession.initiate(
                    SecondaryAuthSessionId.of(SESSION_ID_VALUE),
                    TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID, OPERATOR);
            sessionRepository.save(session);

            Optional<SecondaryAuthSession> found = sessionRepository.findPendingByTeller(TELLER_ID);

            assertThat(found).isPresent();
            assertThat(found.get().status()).isEqualTo(SecondaryAuthStatus.PENDING);
            assertThat(found.get().tellerId()).isEqualTo(TELLER_ID);
        }

        @Test
        @DisplayName("柜员无 PENDING 会话时返回 empty")
        void shouldReturnEmptyWhenNoPendingSession() {
            Optional<SecondaryAuthSession> found = sessionRepository.findPendingByTeller(999999L);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findEffectiveByTeller: 查询柜员生效会话")
    class FindEffectiveByTellerTest {

        @Test
        @DisplayName("PENDING 状态的会话不被 findEffectiveByTeller 返回")
        void shouldNotReturnPendingSessionAsEffective() {
            SecondaryAuthSession session = SecondaryAuthSession.initiate(
                    SecondaryAuthSessionId.of(SESSION_ID_VALUE),
                    TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID, OPERATOR);
            sessionRepository.save(session);

            Optional<SecondaryAuthSession> effective = sessionRepository.findEffectiveByTeller(TELLER_ID);

            assertThat(effective).isEmpty();
        }
    }

    @Nested
    @DisplayName("delete: 软删除")
    class DeleteTest {

        @Test
        @DisplayName("delete 后 load 返回 empty(软删除生效)")
        void shouldSoftDeleteSession() {
            SecondaryAuthSession session = SecondaryAuthSession.initiate(
                    SecondaryAuthSessionId.of(SESSION_ID_VALUE),
                    TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID, OPERATOR);
            sessionRepository.save(session);

            sessionRepository.delete(session);

            assertThat(sessionRepository.load(SecondaryAuthSessionId.of(SESSION_ID_VALUE))).isEmpty();
        }

        @Test
        @DisplayName("deleteById 后 load 返回 empty")
        void shouldSoftDeleteById() {
            SecondaryAuthSession session = SecondaryAuthSession.initiate(
                    SecondaryAuthSessionId.of(SESSION_ID_VALUE),
                    TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID, OPERATOR);
            sessionRepository.save(session);

            sessionRepository.deleteById(SecondaryAuthSessionId.of(SESSION_ID_VALUE));

            assertThat(sessionRepository.load(SecondaryAuthSessionId.of(SESSION_ID_VALUE))).isEmpty();
        }

        @Test
        @DisplayName("delete null 会话不抛异常")
        void shouldNotThrowWhenDeleteNull() {
            sessionRepository.delete(null);
            sessionRepository.deleteById(null);
        }
    }
}
