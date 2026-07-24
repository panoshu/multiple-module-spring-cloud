package com.example.shared.domain.aggregate.entity;

import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.Identifier;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Entity} 基类的单元测试。
 * <p>
 * 覆盖核心契约：
 * <ul>
 *   <li>equals/hashCode 基于类型 + ID 的相等性语义</li>
 *   <li>重建构造函数（Reconstitute）的不变性校验</li>
 *   <li>markUpdated 的版本递增与字段更新</li>
 * </ul>
 *
 * @author panoshu
 * @since 2026/7/24
 */
@DisplayName("Entity 基类测试")
class EntityTest {

    /** 测试用 ID 类型 */
    record TestId(String value) implements Identifier<String> {}

    /** 测试用具体实体 A */
    static class TestEntityA extends Entity<TestId> {
        TestEntityA(TestId id, UserNo createdBy) {
            super(id, createdBy);
            this.validateInvariants();
        }

        TestEntityA(TestId id, UserNo createdBy, UserNo updatedBy,
                    LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
            super(id, createdBy, updatedBy, createdAt, updatedAt, version);
            this.validateInvariants();
        }

        @Override
        protected void validateInvariants() {
            // 测试用空实现，由具体测试场景验证校验调用
        }

        void update(UserNo operator) {
            markUpdated(operator);
        }
    }

    /** 测试用具体实体 B（与 A 不同类型，用于验证类型敏感的 equals） */
    static class TestEntityB extends Entity<TestId> {
        TestEntityB(TestId id, UserNo createdBy) {
            super(id, createdBy);
            this.validateInvariants();
        }

        @Override
        protected void validateInvariants() {
            // 空实现
        }
    }

    /** 不变性校验会抛异常的实体，用于验证重建路径的校验调用 */
    static class StrictEntity extends Entity<TestId> {
        StrictEntity(TestId id, UserNo createdBy, UserNo updatedBy,
                     LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
            super(id, createdBy, updatedBy, createdAt, updatedAt, version);
            this.validateInvariants();
        }

        @Override
        protected void validateInvariants() {
            throw new IllegalStateException("invariant violated");
        }
    }

    @Nested
    @DisplayName("equals 方法")
    class EqualsTest {

        @Test
        @DisplayName("相同类型相同ID应相等")
        void should_be_equal_when_same_type_and_same_id() {
            TestId id = new TestId("id-1");
            UserNo user = UserNo.of("user-1");
            TestEntityA e1 = new TestEntityA(id, user);
            TestEntityA e2 = new TestEntityA(id, user);

            assertThat(e1).isEqualTo(e2);
        }

        @Test
        @DisplayName("相同类型不同ID不应相等")
        void should_not_be_equal_when_same_type_but_different_id() {
            UserNo user = UserNo.of("user-1");
            TestEntityA e1 = new TestEntityA(new TestId("id-1"), user);
            TestEntityA e2 = new TestEntityA(new TestId("id-2"), user);

            assertThat(e1).isNotEqualTo(e2);
        }

        @Test
        @DisplayName("不同类型即使ID相同也不应相等")
        void should_not_be_equal_when_different_type_even_same_id() {
            TestId id = new TestId("id-1");
            UserNo user = UserNo.of("user-1");
            TestEntityA entityA = new TestEntityA(id, user);
            TestEntityB entityB = new TestEntityB(id, user);

            assertThat(entityA).isNotEqualTo(entityB);
        }

        @Test
        @DisplayName("与null比较应返回false")
        void should_return_false_when_compare_with_null() {
            TestEntityA entity = new TestEntityA(new TestId("id-1"), UserNo.of("user-1"));

            assertThat(entity).isNotEqualTo(null);
        }

        @Test
        @DisplayName("与非Entity对象比较应返回false")
        void should_return_false_when_compare_with_non_entity() {
            TestEntityA entity = new TestEntityA(new TestId("id-1"), UserNo.of("user-1"));

            assertThat(entity).isNotEqualTo("id-1");
        }

        @Test
        @DisplayName("与自身比较应返回true")
        void should_return_true_when_compare_with_self() {
            TestEntityA entity = new TestEntityA(new TestId("id-1"), UserNo.of("user-1"));

            assertThat(entity).isEqualTo(entity);
        }
    }

    @Nested
    @DisplayName("hashCode 方法")
    class HashCodeTest {

        @Test
        @DisplayName("相同ID应返回相同hashCode")
        void should_return_same_hashCode_for_same_id() {
            TestId id = new TestId("id-1");
            UserNo user = UserNo.of("user-1");
            TestEntityA e1 = new TestEntityA(id, user);
            TestEntityA e2 = new TestEntityA(id, user);

            assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
        }

        @Test
        @DisplayName("不同ID通常应返回不同hashCode")
        void should_return_different_hashCode_for_different_id() {
            UserNo user = UserNo.of("user-1");
            TestEntityA e1 = new TestEntityA(new TestId("id-1"), user);
            TestEntityA e2 = new TestEntityA(new TestId("id-2"), user);

            assertThat(e1.hashCode()).isNotEqualTo(e2.hashCode());
        }
    }

    @Nested
    @DisplayName("重建构造函数（Reconstitute）")
    class ReconstituteTest {

        @Test
        @DisplayName("应正确保留所有字段")
        void should_preserve_all_fields() {
            TestId id = new TestId("id-1");
            UserNo createdBy = UserNo.of("creator");
            UserNo updatedBy = UserNo.of("updater");
            LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
            LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 2, 11, 30);
            Version version = Version.of(5);

            TestEntityA entity = new TestEntityA(id, createdBy, updatedBy, createdAt, updatedAt, version);

            assertThat(entity.id()).isEqualTo(id);
            assertThat(entity.createdBy()).isEqualTo(createdBy);
            assertThat(entity.updatedBy()).isEqualTo(updatedBy);
            assertThat(entity.createdAt()).isEqualTo(createdAt);
            assertThat(entity.updatedAt()).isEqualTo(updatedAt);
            assertThat(entity.version()).isEqualTo(version);
        }

        @Test
        @DisplayName("ID为null时应抛出IllegalArgumentException")
        void should_throw_when_id_is_null() {
            assertThatThrownBy(() ->
                new TestEntityA(null, UserNo.of("u"), UserNo.of("u"),
                    LocalDateTime.now(), LocalDateTime.now(), Version.initial()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("重建时应调用validateInvariants进行不变性校验")
        void should_invoke_validateInvariants_when_reconstitute() {
            assertThatThrownBy(() ->
                new StrictEntity(new TestId("id-1"), UserNo.of("u"), UserNo.of("u"),
                    LocalDateTime.now(), LocalDateTime.now(), Version.initial()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invariant violated");
        }
    }

    @Nested
    @DisplayName("markUpdated 方法")
    class MarkUpdatedTest {

        @Test
        @DisplayName("应更新updatedBy和updatedAt并递增版本号")
        void should_update_fields_and_increment_version() {
            TestEntityA entity = new TestEntityA(new TestId("id-1"), UserNo.of("creator"));
            Version oldVersion = entity.version();
            UserNo operator = UserNo.of("operator");

            entity.update(operator);

            assertThat(entity.updatedBy()).isEqualTo(operator);
            assertThat(entity.updatedAt()).isAfterOrEqualTo(entity.createdAt());
            assertThat(entity.version().value()).isEqualTo(oldVersion.value() + 1);
        }

        @Test
        @DisplayName("operator为null时应抛出异常")
        void should_throw_when_operator_is_null() {
            TestEntityA entity = new TestEntityA(new TestId("id-1"), UserNo.of("creator"));

            assertThatThrownBy(() -> entity.update(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
