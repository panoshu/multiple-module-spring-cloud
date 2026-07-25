package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.HqUserId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class HqUserTest {

    @Test
    void create_should_return_active_user_with_correct_fields() {
        UserNo operator = UserNo.of("U001");
        HqUser user = HqUser.create(
            HqUserId.of(1L), "S001", "admin001", "管理员", "IT", operator
        );

        assertEquals(HqUserId.of(1L), user.id());
        assertEquals("S001", user.staffNo());
        assertEquals("admin001", user.loginName());
        assertEquals("管理员", user.displayName());
        assertEquals("IT", user.department());
        assertEquals(UserStatus.ACTIVE, user.status());
        assertEquals(operator, user.createdBy());
        assertEquals(Version.initial(), user.version());
    }

    @Test
    void disable_should_mark_user_disabled_and_increment_version() {
        HqUser user = createActiveUser();
        Version oldVersion = user.version();

        user.disable(UserNo.of("U002"));

        assertEquals(UserStatus.DISABLED, user.status());
        assertTrue(user.version().value() > oldVersion.value());
    }

    @Test
    void enable_should_mark_user_active() {
        HqUser user = createActiveUser();
        user.disable(UserNo.of("U002"));
        user.enable(UserNo.of("U003"));
        assertEquals(UserStatus.ACTIVE, user.status());
    }

    @Test
    void reconstitute_should_rebuild_user_from_persistence() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 25, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 7, 25, 11, 0);

        HqUser user = HqUser.reconstitute(
            HqUserId.of(1L), "S001", "admin001", "管理员", "IT",
            UserStatus.ACTIVE, null, null,
            UserNo.of("U001"), UserNo.of("U002"),
            created, updated, Version.of(3L)
        );

        assertEquals(Version.of(3L), user.version());
        assertEquals(created, user.createdAt());
        assertEquals("IT", user.department());
    }

    @Test
    void create_should_throw_when_login_name_blank() {
        assertThrows(IllegalArgumentException.class, () ->
            HqUser.create(HqUserId.of(1L), "S001", "", "管理员", "IT", UserNo.of("U001"))
        );
    }

    @Test
    void create_should_throw_when_staff_no_blank() {
        assertThrows(IllegalArgumentException.class, () ->
            HqUser.create(HqUserId.of(1L), "", "admin001", "管理员", "IT", UserNo.of("U001"))
        );
    }

    private HqUser createActiveUser() {
        return HqUser.create(HqUserId.of(1L), "S001", "admin001", "管理员", "IT", UserNo.of("U001"));
    }
}
