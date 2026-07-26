package com.example.iam.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdTypesTest {

  @Test
  void userIdOfShouldReturnValue() {
    UserId id = UserId.of(123L);
    assertEquals(123L, id.value());
    assertEquals(123L, id.longValue());
  }

  @Test
  void allIdTypesShouldBeEqualWhenSameValue() {
    assertEquals(UserId.of(1L), UserId.of(1L));
    assertEquals(CredentialId.of(1L), CredentialId.of(1L));
    assertEquals(SecondaryAuthSessionId.of(1L), SecondaryAuthSessionId.of(1L));
    assertEquals(LoginLogId.of(1L), LoginLogId.of(1L));
    assertEquals(PermissionRuleId.of(1L), PermissionRuleId.of(1L));
    assertEquals(PlanDelegationId.of(1L), PlanDelegationId.of(1L));
    assertEquals(BusinessDefinitionId.of(1L), BusinessDefinitionId.of(1L));
    assertEquals(RouteRuleId.of(1L), RouteRuleId.of(1L));
  }

  @Test
  void ofStringShouldParseLong() {
    assertEquals(UserId.of(42L), UserId.of("42"));
    assertEquals(PermissionRuleId.of(99L), PermissionRuleId.of("99"));
  }

  @Test
  void ofNullShouldThrow() {
    assertThrows(NullPointerException.class, () -> UserId.of((Long) null));
    assertThrows(NullPointerException.class, () -> CredentialId.of((Long) null));
    assertThrows(NullPointerException.class, () -> SecondaryAuthSessionId.of((Long) null));
    assertThrows(NullPointerException.class, () -> LoginLogId.of((Long) null));
    assertThrows(NullPointerException.class, () -> PermissionRuleId.of((Long) null));
    assertThrows(NullPointerException.class, () -> PlanDelegationId.of((Long) null));
    assertThrows(NullPointerException.class, () -> BusinessDefinitionId.of((Long) null));
    assertThrows(NullPointerException.class, () -> RouteRuleId.of((Long) null));
  }

  @Test
  void ofInvalidStringShouldThrow() {
    assertThrows(NumberFormatException.class, () -> UserId.of("abc"));
  }

  @Test
  void differentIdTypesShouldNotBeEqual() {
    assertNotEquals(UserId.of(1L), CredentialId.of(1L));
  }
}
