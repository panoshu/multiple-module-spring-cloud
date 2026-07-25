package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.BranchUserId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BranchUserTest {

    @Test
    void create_should_return_active_user_with_correct_fields() {
        UserNo operator = UserNo.of("U001");
        BranchUser user = BranchUser.create(
            BranchUserId.of(1L), "B001", "BR001", "T001", "teller001", "柜员", operator
        );

        assertEquals(BranchUserId.of(1L), user.id());
        assertEquals("B001", user.bankCode());
        assertEquals("BR001", user.branchCode());
        assertEquals("T001", user.tellerNo());
        assertEquals("teller001", user.loginName());
        assertEquals("柜员", user.displayName());
        assertEquals(UserStatus.ACTIVE, user.status());
        assertEquals(Version.initial(), user.version());
    }

    @Test
    void disable_should_mark_user_disabled_and_increment_version() {
        BranchUser user = createActiveUser();
        Version oldVersion = user.version();

        user.disable(UserNo.of("U002"));

        assertEquals(UserStatus.DISABLED, user.status());
        assertTrue(user.version().value() > oldVersion.value());
    }

    @Test
    void enable_should_mark_user_active() {
        BranchUser user = createActiveUser();
        user.disable(UserNo.of("U002"));
        user.enable(UserNo.of("U003"));
        assertEquals(UserStatus.ACTIVE, user.status());
    }

    @Test
    void reconstitute_should_rebuild_user_from_persistence() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 25, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 7, 25, 11, 0);

        BranchUser user = BranchUser.reconstitute(
            BranchUserId.of(1L), "B001", "BR001", "T001", "teller001", "柜员",
            UserStatus.ACTIVE, null, null,
            UserNo.of("U001"), UserNo.of("U002"),
            created, updated, Version.of(3L)
        );

        assertEquals(Version.of(3L), user.version());
        assertEquals("B001", user.bankCode());
    }

    @Test
    void create_should_throw_when_login_name_blank() {
        assertThrows(IllegalArgumentException.class, () ->
            BranchUser.create(BranchUserId.of(1L), "B001", "BR001", "T001", "", "柜员", UserNo.of("U001"))
        );
    }

    @Test
    void create_should_throw_when_teller_no_blank() {
        assertThrows(IllegalArgumentException.class, () ->
            BranchUser.create(BranchUserId.of(1L), "B001", "BR001", "", "teller001", "柜员", UserNo.of("U001"))
        );
    }

    private BranchUser createActiveUser() {
        return BranchUser.create(BranchUserId.of(1L), "B001", "BR001", "T001", "teller001", "柜员", UserNo.of("U001"));
    }
}
