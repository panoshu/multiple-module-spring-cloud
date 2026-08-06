package com.pension.permission.domain.authorization.valueobject.subject;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;
import com.pension.permission.types.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GrantSubject 主体匹配测试")
class GrantSubjectTest {

  @Nested
  @DisplayName("CapabilitySubject")
  class CapabilitySubjectTest {

    @Test
    @DisplayName("covers 应抛 UnsupportedOperationException（能力层不参与主体匹配）")
    void shouldThrowOnCovers() {
      var subject = new CapabilitySubject();
      var lookup = mock(PlanMembershipLookup.class);
      assertThatThrownBy(() -> subject.covers(UserNo.of("u-1"), lookup))
        .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("PlanAllMembersSubject")
  class PlanAllMembersSubjectTest {

    @Test
    @DisplayName("用户是计划成员时应返回 true")
    void shouldReturnTrueWhenUserIsMember() {
      var planNo = PlanNo.of("PLAN-001");
      var subject = new PlanAllMembersSubject(planNo);
      var lookup = mock(PlanMembershipLookup.class);
      when(lookup.isMemberOf(UserNo.of("u-1"), planNo)).thenReturn(true);

      assertThat(subject.covers(UserNo.of("u-1"), lookup)).isTrue();
    }

    @Test
    @DisplayName("用户非计划成员时应返回 false")
    void shouldReturnFalseWhenUserNotMember() {
      var planNo = PlanNo.of("PLAN-001");
      var subject = new PlanAllMembersSubject(planNo);
      var lookup = mock(PlanMembershipLookup.class);
      when(lookup.isMemberOf(UserNo.of("u-1"), planNo)).thenReturn(false);

      assertThat(subject.covers(UserNo.of("u-1"), lookup)).isFalse();
    }
  }

  @Nested
  @DisplayName("PlanRoleSubject")
  class PlanRoleSubjectTest {

    @Test
    @DisplayName("用户具有计划内指定角色时应返回 true")
    void shouldReturnTrueWhenUserHasRole() {
      var planNo = PlanNo.of("PLAN-001");
      var roleCode = new RoleCode("AGENT");
      var subject = new PlanRoleSubject(planNo, roleCode);
      var lookup = mock(PlanMembershipLookup.class);
      when(lookup.hasRole(UserNo.of("u-1"), planNo, roleCode)).thenReturn(true);

      assertThat(subject.covers(UserNo.of("u-1"), lookup)).isTrue();
    }
  }

  @Nested
  @DisplayName("UserListSubject")
  class UserListSubjectTest {

    @Test
    @DisplayName("用户在列表中时应返回 true")
    void shouldReturnTrueWhenUserInList() {
      var subject = new UserListSubject(Set.of(UserNo.of("u-1"), UserNo.of("u-2")));
      var lookup = mock(PlanMembershipLookup.class);

      assertThat(subject.covers(UserNo.of("u-1"), lookup)).isTrue();
    }

    @Test
    @DisplayName("用户不在列表中时应返回 false")
    void shouldReturnFalseWhenUserNotInList() {
      var subject = new UserListSubject(Set.of(UserNo.of("u-1")));
      var lookup = mock(PlanMembershipLookup.class);

      assertThat(subject.covers(UserNo.of("u-999"), lookup)).isFalse();
    }
  }
}
