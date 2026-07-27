# Task 3 Report: 功能权限注解与 AOP 拦截器

## Status: DONE

## Files Created/Modified

**Created:**
- `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-adapter\src\main\java\com\example\core\adapter\security\RequireBusinessPermission.java`
- `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-adapter\src\main\java\com\example\core\adapter\security\BusinessPermissionAspect.java`
- `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-adapter\src\test\java\com\example\core\adapter\security\BusinessPermissionAspectTest.java`

**Modified:**
- `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-adapter\pom.xml` (added `spring-boot-starter-aop` dependency)

## Commits

- `9339d2f` — `feat(core-adapter): 新增功能权限注解与 AOP 拦截器`

```
1. @RequireBusinessPermission 注解标注在 Controller 方法上声明所需权限码
2. BusinessPermissionAspect 通过 AOP 拦截注解方法,校验会话用户的 permissionCodes
3. 用于垂直越权防护(功能权限),业务类型办理权限由 BusinessAccessGuard 校验
```

Files in commit (4 files changed, 180 insertions(+)):
- `business-core-kernel/business-core-adapter/pom.xml` (+6)
- `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/security/BusinessPermissionAspect.java` (+45)
- `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/security/RequireBusinessPermission.java` (+34)
- `business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/security/BusinessPermissionAspectTest.java` (+95)

## Test Results

**TDD Discipline Followed:**
- Step 2 (RED): Wrote test first → ran → expected compilation failure (missing `BusinessPermissionAspect`, missing `org.aspectj.lang` package, missing `ProceedingJoinPoint`). Confirmed failure.
- Step 4 (GREEN): Implemented `BusinessPermissionAspect` + added AOP dependency → tests passed.

**Final Test Run:**

Command: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=BusinessPermissionAspectTest`

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.679 s -- in com.example.core.adapter.security.BusinessPermissionAspectTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Both tests pass:
- `should_pass_when_permission_present` — verifies that when `session.permissionCodes()` contains the required code, the join point proceeds and returns `"ok"`.
- `should_fail_when_permission_missing` — verifies that when permissions are missing, `BusinessException` is thrown with `displayMessage()` containing "无功能权限".

## Self-Review Checklist

| # | Item | Status |
|---|------|--------|
| 1 | Both tests pass (`should_pass_when_permission_present` + `should_fail_when_permission_missing`) | ✅ Pass |
| 2 | `mvn test -pl business-core-kernel/business-core-adapter -Dtest=BusinessPermissionAspectTest` passes | ✅ BUILD SUCCESS |
| 3 | Mock annotation fix applied (test passes mock `RequireBusinessPermission`, not String) | ✅ Applied via `mockPermission(String)` helper |
| 4 | `displayMessage()` assertion fix applied to failing test | ✅ Uses `.extracting(ex -> ((BusinessException) ex).displayMessage()).asString().contains("无功能权限")` |
| 5 | `@RequireBusinessPermission` annotation has `@Target(METHOD)` + `@Retention(RUNTIME)` | ✅ Verified |
| 6 | `BusinessPermissionAspect` has `@Aspect` + `@Component` + `@Around("@annotation(requirePermission)")` | ✅ Verified |
| 7 | Javadoc present on all public classes/methods with `@author panoshu` | ✅ Verified on annotation, aspect, and test class |
| 8 | `spring-boot-starter-aop` added to adapter pom.xml | ✅ Added as compile-scope dependency |
| 9 | Commit message follows Conventional Commits with scope `core-adapter` | ✅ `feat(core-adapter): ...` |

## Brief Deviation Fixes Applied

### Concern 1: Test signature mismatch (BRIEF DEFECT — fixed)

**Problem:** The brief test called `aspect.checkPermission(pjp, "BATCH_CREATE")` passing a String, but the implementation signature takes `RequireBusinessPermission` annotation — this would not compile.

**Fix Applied:** Added a `mockPermission(String)` helper that creates a Mockito mock of `RequireBusinessPermission` annotation with `when(annotation.value()).thenReturn(value)`. Updated both test methods to call:
- `aspect.checkPermission(pjp, mockPermission("BATCH_CREATE"))`

The implementation stays verbatim per brief (signature unchanged).

### Concern 2: `hasMessageContaining` doesn't work for BusinessException (fixed)

**Problem:** The brief test used `.hasMessageContaining("无功能权限")`, but `BusinessException.getMessage()` returns `errorInfo()` (e.g., `[COMMON.0003] 无权限访问`), NOT `userDetail`. The "无功能权限" string is in `userDetail`, not in `getMessage()`.

**Fix Applied (consistent with Tasks 1 & 2):** Replaced `.hasMessageContaining("无功能权限")` with:
```java
.extracting(ex -> ((BusinessException) ex).displayMessage())
.asString()
.contains("无功能权限");
```

`displayMessage()` returns `message + "，" + userDetail` = `"无权限访问，无功能权限"`, which contains "无功能权限".

### Concern 3: `spring-boot-starter-aop` dependency

Added as a regular (compile-scope, no `<scope>` declaration) dependency in `business-core-kernel/business-core-adapter/pom.xml`. This provides:
- `org.aspectj.lang.*` (ProceedingJoinPoint, etc.)
- `org.aspectj.lang.annotation.*` (@Aspect, @Around, etc.)
- Spring AOP infrastructure for proxy creation at runtime

The dependency version is managed by `spring-boot-dependencies` BOM inherited from the parent POM.

## Implementation Notes

### Architectural Decisions Preserved (per task brief)

1. **`@RequireBusinessPermission` = functional permission (vertical):** Guards whether a user can access a feature endpoint at all (e.g., "can user access BATCH_CREATE endpoint?").
2. **`BusinessAccessGuard` (from Task 2) = data permission (horizontal):** Guards whether a user can handle a specific plan/businessType. Not touched in this task.
3. **Aspect uses `SessionContextResolver` (from Task 1):** Internally uses `RequestContextHolder` — keeps API interface signatures pure (no `HttpServletRequest` parameter leaking into Controller methods).

### Test Setup Pattern

The test sets up `RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()))` in `@BeforeEach` to simulate a web request context (matching the pattern from `SessionContextResolverTest`). It cleans up via `RequestContextHolder.resetRequestAttributes()` in `@AfterEach`.

## Concerns

None. All three known concerns (test signature mismatch, displayMessage assertion, AOP dependency) were addressed as specified in the task description. Implementation matches the brief verbatim except for the two prescribed test fixes.
