# business-core-kernel 公共 API 实现进度账本

## 已完成任务

### Task 1: 会话上下文基础设施 ✅
- **Commit**: `bb8d1be` feat(core-api): 新增会话上下文与业务元数据组装基础设施
- **状态**: APPROVED (task reviewer)
- **测试**: 5/5 通过 (SessionContextResolverTest 3 + BusinessMetaContextAssemblerTest 2)

#### Minor findings (留待最终 whole-branch review 处理)
1. **导入顺序** `SessionContextResolver.java` 第 101 行: `import java.io.IOException;` 按字母序应位于 `jakarta.*` 之后。IDE 自动优化即可。
2. **测试断言写法** `SessionContextResolverTest.java` 第 41 行、`BusinessMetaContextAssemblerTest.java` 第 31 行: `.extracting(throwable -> ((BusinessException) throwable).displayMessage())` 可简化为 `.extracting(BusinessException::displayMessage)`。风格优化。
3. **Commit body 多空行**: PowerShell 多 `-m` 参数导致 commit body 各编号项之间多出空行。非违规,符合规则"禁止使用 HEREDOC,改用多个 -m 参数"的副作用。

#### Brief 偏差修复 (已验证合法)
1. `resolver.resolve(request)` → `resolver.require(request)` (brief 笔误,实现无 resolve 方法)
2. 测试方法添加 `throws Exception` (writeValueAsString 抛 checked 异常)
3. `hasMessageContaining` → `extracting(...displayMessage())` (BusinessException.getMessage() 返回 errorInfo 不含 userDetail)
4. `catch (JsonProcessingException|IllegalArgumentException)` → `catch (IOException|IllegalArgumentException)` (readValue 抛 IOException 父类)

### Task 2: 权限校验 SPI 与业务类型注册 ✅
- **Commit**: `ed8ead8` feat(core-application): 新增权限校验 SPI 与业务类型注册基础设施
- **状态**: APPROVED (task reviewer)
- **测试**: 11/11 通过 (DefaultBusinessAccessGuardTest 8 + SupportedBusinessTypeValidatorTest 3)

#### Minor findings (留待最终 whole-branch review 处理)
4. **新增错误码未被引用**: `UNSUPPORTED_BUSINESS_TYPE`/`PLAN_MISMATCH`/`PROXY_FORBIDDEN`/`SECONDARY_AUTH_REQUIRED` 在当前任务未被使用(实现用 CommonError + withUserDetail)。Brief 设计选择,非缺陷。最终 review 确认后续任务是否引用。
5. **default 分支缺测试**: `DefaultBusinessAccessGuard` switch 的 default 分支(未知渠道)无测试覆盖。非阻塞,brief 未要求。

#### Brief 偏差修复 (已验证合法)
1. `displayMessage()` 断言修复应用于 6 处测试方法 (同 Task 1 模式)
2. `spring-boot-autoconfigure` 添加为 provided 依赖 (编译需要 @ConditionalOnMissingBean)

#### 已知延期关注点 (Task 9 处理)
- `@ConditionalOnMissingBean` 在 `@Component` 上无效。当前保留 brief 原文。Task 9 auto-configuration 需重构为 `@Configuration` + `@Bean` + `@ConditionalOnMissingBean`,移除 `DefaultBusinessAccessGuard` 上的 `@Component`。

### Task 3: 功能权限注解与 AOP 拦截器 ✅
- **Commit**: `9339d2f` feat(core-adapter): 新增功能权限注解与 AOP 拦截器
- **状态**: APPROVED (task reviewer)
- **测试**: 2/2 通过 (BusinessPermissionAspectTest)

#### Minor findings (留待最终 whole-branch review 处理)
6. **`@Slf4j` 未使用**: `BusinessPermissionAspect` 标注 `@Slf4j` 但无 log 调用。Brief 原样保留。可移除或后续补充审计日志。
7. **不必要桩**: `mockJoinPoint()` 中 `when(pjp.getArgs()).thenReturn(...)` 未被切面使用。Brief 原样保留。严格模式可能报错。
8. **⚠️ AOP 代理绑定未验证**: 单元测试直接调用方法,未通过 Spring AOP 代理触发切点。切点表达式 `@annotation(requirePermission)` 的参数名绑定需集成测试覆盖。建议 Task 9 集成验证时补充。

#### Brief 偏差修复 (已验证合法)
1. Mock 注解修复: 测试传 mock `RequireBusinessPermission` 而非 String (brief 签名不匹配)
2. `displayMessage()` 断言修复 (同 Tasks 1 & 2)

### Task 4: BusinessBatchApi 接口与 DTO 定义 ✅
- **Commit**: `57f72f9` feat(core-api): 新增 BusinessBatchApi 接口与配套 DTO
- **状态**: APPROVED (task reviewer)
- **编译**: BUILD SUCCESS (无测试,仅编译验证)

#### Minor findings (留待最终 whole-branch review 处理)
9. **未使用 import**: `CancelBatchCommand.java` 导入 `NotBlank` 但未使用(仅用 `@NotNull`)。Brief 原样保留。可清理。

### Task 5: BusinessBatchAppService 与 Controller 实现 ✅
- **Commit**: `ab9bc85` feat(core-adapter): 实现 BusinessBatchApi 与应用服务
- **状态**: APPROVED (task reviewer)
- **测试**: 5/5 通过 (BusinessBatchAppServiceTest)

#### Brief 偏差修复 (已验证合法)
1. **BatchConverter 改用 default 方法**: brief 的 `@Mapping(source="id.value"/"createdAt"/"status.name"/"businessFormRefs.size")` 在 MapStruct 1.6.3 下无法编译 — `BusinessBatch` 继承泛型 `Entity<ID>`,基类访问器 `id()`/`createdAt()` 不遵循 JavaBean 命名。改用 `@Mapper(componentModel="spring")` + `default` 方法,与 `annuity-infrastructure/BatchDataConverter` 同一模式。
2. **测试命令补 `-am`**: 本机本地仓库 domain JAR 旧,需 `-am` 重新编译上游模块。
3. **`cancel()` 未调用 `markUpdated()`**: 按 brief 原文实现,brief 瑕疵,留待最终 review。

#### Minor findings (留待最终 whole-branch review 处理)
10. **`cancel(reason)` 的 reason 未使用**: `BatchStatusChangedEvent.of()` 不接收 reason。Brief 瑕疵。
11. **`cancel()` 未调用 `markUpdated()`**: 违反规则 04 §7。Brief 瑕疵。
12. **`BatchDetailResponse.forms` 恒为 null**: brief `@Mapping(target="forms", ignore=true)` 直译。
13. **测试未覆盖负面路径**: `cancel` 终态失败、`findActive` 返回空 Optional 未测。
14. **未使用 import**: `BusinessBatchAppServiceTest.java` 导入 `any` 未使用。
15. **事件双发布语义混淆**: `cancel()` 中 `eventBus.publish` 在 Repository 已发布事件时为空操作。整支评审统一职责。
16. **提交 scope 应为 `kernel`**: 跨 4 个子模块,按规则 09 §3.2 应使用 `kernel` 而非 `core-adapter`。Brief 瑕疵。

#### ⚠️ 跨任务遗留 (CV1 - 必须在 annuity-service 下一个任务处理)
- `annuity-service/annuity-infrastructure/.../BatchRepositoryImpl.java` 未实现新增的 `findActive(PlanNo, BusinessType)` 方法。当前 business-core-kernel 编译通过,但构建 annuity-service 时将编译失败。需基于 `t_annuity_batch` 表查 `plan_no` + `business_type` + `status IN (CREATED, PROCESSING)` + `deleted = 0`。

#### ⚠️ 跨任务遗留 (CV2 - 可选优化)
- `annuity-infrastructure/.../BatchDataConverter.java` 仍通过 `KernelAggregateReflector` 反射访问 `BusinessBatch` 私有字段。本任务已补齐 7 个公开 getter,后续可改用 getter 移除反射依赖。

---

## 待完成任务
- Task 6: BusinessFormApi 接口与实现
- Task 7: BusinessApplicationApi 接口与实现
- Task 8: MaterialAppApi 与 BusinessProgressApi 接口与实现
- Task 9: 自动装配与集成验证
- 最终: whole-branch code review
