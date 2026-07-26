package com.example.iam.domain.errorcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.iam.domain.system.errorcode.IamSystemErrorCode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * iam-service 错误码三件套契约测试。
 *
 * <p>验证所有错误码满足 {@code 08-错误码规范.md} 约束:
 * <ul>
 *   <li>code 全局唯一(三件套合并不重复)</li>
 *   <li>code 格式为 {@code SERVICE.IAM.XXXX} 4 位数字</li>
 *   <li>code 与 message 非空非 blank</li>
 *   <li>message 不包含 {} 占位符或方括号前缀</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
class IamErrorCodesContractTest {

  private static final Pattern CODE_PATTERN = Pattern.compile("^SERVICE\\.IAM\\.\\d{4}$");

  @Test
  void authErrorCodesShouldHave35Entries() {
    assertEquals(35, IamAuthErrorCode.values().length);
  }

  @Test
  void authzErrorCodesShouldHave38Entries() {
    assertEquals(38, IamAuthzErrorCode.values().length);
  }

  @Test
  void systemErrorCodesShouldHave28Entries() {
    assertEquals(28, IamSystemErrorCode.values().length);
  }

  @Test
  void authCodesShouldBeInCorrectRange() {
    for (IamAuthErrorCode code : IamAuthErrorCode.values()) {
      int seq = Integer.parseInt(code.code().substring("SERVICE.IAM.".length()));
      assertTrue(seq >= 1 && seq <= 99,
          "Auth error code %s out of range [0001, 0099]".formatted(code.code()));
    }
  }

  @Test
  void authzCodesShouldBeInCorrectRange() {
    for (IamAuthzErrorCode code : IamAuthzErrorCode.values()) {
      int seq = Integer.parseInt(code.code().substring("SERVICE.IAM.".length()));
      assertTrue(seq >= 100 && seq <= 199,
          "Authz error code %s out of range [0100, 0199]".formatted(code.code()));
    }
  }

  @Test
  void systemCodesShouldBeInCorrectRange() {
    for (IamSystemErrorCode code : IamSystemErrorCode.values()) {
      int seq = Integer.parseInt(code.code().substring("SERVICE.IAM.".length()));
      assertTrue(seq >= 200 && seq <= 299,
          "System error code %s out of range [0200, 0299]".formatted(code.code()));
    }
  }

  @Test
  void allCodesShouldBeGloballyUnique() {
    List<Enum<? extends com.example.shared.exception.ErrorDefinition>> all =
        new java.util.ArrayList<>();
    all.addAll(Arrays.asList(IamAuthErrorCode.values()));
    all.addAll(Arrays.asList(IamAuthzErrorCode.values()));
    all.addAll(Arrays.asList(IamSystemErrorCode.values()));

    Set<String> seen = new HashSet<>();
    for (Enum<? extends com.example.shared.exception.ErrorDefinition> e : all) {
      com.example.shared.exception.ErrorDefinition def = (com.example.shared.exception.ErrorDefinition) e;
      String code = def.code();
      assertTrue(seen.add(code), "Duplicate error code: " + code);
    }
    assertEquals(35 + 38 + 28, seen.size());
  }

  @Test
  void allCodesShouldMatchServiceIamFormat() {
    for (IamAuthErrorCode c : IamAuthErrorCode.values()) {
      assertCodeFormat(c);
    }
    for (IamAuthzErrorCode c : IamAuthzErrorCode.values()) {
      assertCodeFormat(c);
    }
    for (IamSystemErrorCode c : IamSystemErrorCode.values()) {
      assertCodeFormat(c);
    }
  }

  private static void assertCodeFormat(com.example.shared.exception.ErrorDefinition def) {
    assertNotNull(def.code(), "Code is null for " + def);
    assertNotNull(def.message(), "Message is null for " + def);
    assertFalse(def.code().isBlank(), "Code is blank");
    assertFalse(def.message().isBlank(), "Message is blank");
    assertTrue(CODE_PATTERN.matcher(def.code()).matches(),
        "Code %s does not match SERVICE.IAM.XXXX".formatted(def.code()));
    assertFalse(def.message().contains("{}"),
        "Message contains {} placeholder: " + def.message());
    assertFalse(def.message().startsWith("["),
        "Message starts with bracket: " + def.message());
  }
}
