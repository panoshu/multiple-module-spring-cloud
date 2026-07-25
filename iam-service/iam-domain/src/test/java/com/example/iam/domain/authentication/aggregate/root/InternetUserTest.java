package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.InternetUserId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InternetUserTest {

    @Test
    void create_should_return_active_user_with_correct_fields() {
        UserNo operator = UserNo.of("U001");
        InternetUser user = InternetUser.create(
            InternetUserId.of(1L), CustomerNo.of("C001"),
            "hr001", "张三", operator
        );

        assertEquals(InternetUserId.of(1L), user.id());
        assertEquals(CustomerNo.of("C001"), user.customerNo());
        assertEquals("hr001", user.loginName());
        assertEquals("张三", user.displayName());
        assertEquals(UserStatus.ACTIVE, user.status());
        assertEquals(operator, user.createdBy());
        assertEquals(Version.initial(), user.version());
    }

    @Test
    void disable_should_mark_user_disabled_and_increment_version() {
        InternetUser user = createActiveUser();
        Version oldVersion = user.version();

        user.disable(UserNo.of("U002"));

        assertEquals(UserStatus.DISABLED, user.status());
        assertTrue(user.version().value() > oldVersion.value());
    }

    @Test
    void enable_should_mark_user_active() {
        InternetUser user = createActiveUser();
        user.disable(UserNo.of("U002"));
        user.enable(UserNo.of("U003"));
        assertEquals(UserStatus.ACTIVE, user.status());
    }

    @Test
    void lock_should_mark_user_locked() {
        InternetUser user = createActiveUser();
        user.lock(UserNo.of("U002"));
        assertEquals(UserStatus.LOCKED, user.status());
    }

    @Test
    void reconstitute_should_rebuild_user_from_persistence() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 25, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 7, 25, 11, 0);

        InternetUser user = InternetUser.reconstitute(
            InternetUserId.of(1L), CustomerNo.of("C001"), "hr001", "张三",
            UserStatus.ACTIVE, null, null,
            UserNo.of("U001"), UserNo.of("U002"),
            created, updated, Version.of(3L)
        );

        assertEquals(Version.of(3L), user.version());
        assertEquals(created, user.createdAt());
    }

    @Test
    void create_should_throw_when_login_name_blank() {
        assertThrows(IllegalArgumentException.class, () ->
            InternetUser.create(InternetUserId.of(1L), CustomerNo.of("C001"), "", "张三", UserNo.of("U001"))
        );
    }

    private InternetUser createActiveUser() {
        return InternetUser.create(InternetUserId.of(1L), CustomerNo.of("C001"), "hr001", "张三", UserNo.of("U001"));
    }
}
